package qupath.ext.fireparser.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.projects.ProjectImageEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Controller for managing project entry name prefixes based on folder sublevels
 */
public class PrefixController extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(PrefixController.class);
    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.fireparser.ui.strings");
    
    @FXML
    private Spinner<Integer> sublevelSpinner;
    
    @FXML
    private Label infoLabel;
    
    @FXML
    private Button applyButton;
    
    @FXML
    private Button cancelButton;
    
    private final QuPathGUI qupath;
    private final Map<ProjectImageEntry<?>, String> originalNames;
    private final Map<String, List<ProjectImageEntry<?>>> pathGroups;
    private final int maxSublevels;
    private int currentSublevels = 0;
    
    /**
     * Create a new instance of the prefix controller.
     */
    public static PrefixController createInstance(QuPathGUI qupath) throws IOException {
        return new PrefixController(qupath);
    }
    
    private PrefixController(QuPathGUI qupath) throws IOException {
        this.qupath = qupath;
        this.originalNames = new LinkedHashMap<>();
        this.pathGroups = new LinkedHashMap<>();
        
        var url = PrefixController.class.getResource("prefix.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();
        
        // Collect all project entries and their paths
        var project = qupath.getProject();
        if (project == null) {
            throw new IllegalStateException("No project is currently open");
        }
        
        var entries = project.getImageList();
        if (entries.isEmpty()) {
            throw new IllegalStateException("Project has no images");
        }
        
        // Store original names and analyze paths
        int maxLevels = 0;
        for (var entry : entries) {
            originalNames.put(entry, entry.getImageName());
            
            var uris = entry.getURIs();
            if (uris != null && !uris.isEmpty()) {
                String uriString = uris.iterator().next().toString();
                // Strip query params (OMERO)
                int queryIndex = uriString.indexOf('?');
                String cleanUri = (queryIndex != -1) ? uriString.substring(0, queryIndex) : uriString;
                
                String[] parts = cleanUri.split("/");
                // The last part is the filename, components before it are "folders"
                int levels = Math.max(0, parts.length - 1); 
                
                // Use the folder structure as the path key for grouping
                String pathKey = cleanUri.substring(0, Math.max(0, cleanUri.lastIndexOf('/')));
                pathGroups.computeIfAbsent(pathKey, k -> new ArrayList<>()).add(entry);
                
                maxLevels = Math.max(maxLevels, levels);
            }
        }
        this.maxSublevels = maxLevels;
        
        // Detect current sublevel by checking if names already have prefixes
        currentSublevels = detectCurrentSublevels(entries);
        
        // Initialize spinner
        SpinnerValueFactory<Integer> valueFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxSublevels, currentSublevels);
        sublevelSpinner.setValueFactory(valueFactory);
        
        // Add listener to update project entries in real-time
        sublevelSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateProjectEntries(newVal));
        
        // Set up buttons
        applyButton.setOnAction(e -> onApply());
        cancelButton.setOnAction(e -> onCancel());
        
        // Initial update
        updateProjectEntries(currentSublevels);
        updateInfoLabel();
    }
    
    /**
     * Detect current sublevel setting by checking if current entry names 
     * match the names that would be generated at each possible sublevel.
     */
    private int detectCurrentSublevels(Collection<? extends ProjectImageEntry<?>> entries) {
        // Start from the most specific (max) and work down to 0
        for (int i = maxSublevels; i > 0; i--) {
            boolean allMatch = true;
            
            for (var entry : entries) {
                String currentName = entry.getImageName();
                // generateNewName handles stripping existing prefixes via removePrefix()
                // so we can pass the current name as the 'base'
                String theoreticalName = generateNewName(entry, currentName, i);
                
                if (!currentName.equals(theoreticalName)) {
                    allMatch = false;
                    break;
                }
            }
            
            // If every entry in the project matches the pattern for level i, 
            // then i is our current state.
            if (allMatch) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Update all project entries with new names based on sublevel setting
     */
    private void updateProjectEntries(int sublevels) {
        for (var entry : originalNames.keySet()) {
            String originalName = originalNames.get(entry);
            String newName = generateNewName(entry, originalName, sublevels);
            entry.setImageName(newName);
        }
        
        // Force refresh of the project view
        var project = qupath.getProject();
        if (project != null) {
            qupath.refreshProject();
        }
        
        updateInfoLabel();
    }
    
    private String generateNewName(ProjectImageEntry<?> entry, String originalName, int sublevels) {
        if (sublevels == 0) {
            // Remove any existing prefix
            return removePrefix(originalName);
        }
        
        try {
            // Get the URIs for the image (handles local and remote like OMERO)
            var uris = entry.getURIs();
            if (uris == null || uris.isEmpty()) {
                return originalName;
            }

            // Get the first URI string and strip query parameters (common in OMERO)
            String uriString = uris.iterator().next().toString();
            int queryIndex = uriString.indexOf('?');
            String cleanUri = (queryIndex != -1) ? uriString.substring(0, queryIndex) : uriString;

            // Split by forward slash to handle path components agnostic of protocol
            String[] parts = cleanUri.split("/");
            
            // The last part is the filename, so the folder levels are before it
            int lastPartIndex = parts.length - 1;
            int startIndex = Math.max(0, lastPartIndex - sublevels);

            List<String> pathComponents = new ArrayList<>();
            for (int i = startIndex; i < lastPartIndex; i++) {
                // Filter out protocol markers (like "file:") or empty segments
                if (!parts[i].isEmpty() && !parts[i].contains(":")) {
                    pathComponents.add(parts[i]);
                }
            }

            if (pathComponents.isEmpty()) {
                return removePrefix(originalName);
            }

            // Build the prefix
            String prefix = String.join("/", pathComponents) + "/";
            String baseName = removePrefix(originalName);

            return prefix + baseName;

        } catch (IOException e) {
            logger.warn("Could not retrieve URIs for entry {}: {}", entry.getImageName(), e.getMessage());
            return originalName;
        }
    }   

    private String removePrefix(String name) {
        // Remove any path prefix before the actual filename
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            return name.substring(lastSlash + 1);
        }
        return name;
    }
    
    private void updateInfoLabel() {
        int sublevel = sublevelSpinner.getValue();
        int totalEntries = originalNames.size();
        int uniquePaths = pathGroups.size();
        
        String info = String.format("Sublevels: %d | Images: %d | Unique paths: %d | Max possible: %d",
            sublevel, totalEntries, uniquePaths, maxSublevels);
        infoLabel.setText(info);
    }
    
    @FXML
    private void onApply() {
        int sublevels = sublevelSpinner.getValue();
        
        // Update the original names map to reflect the current state as "saved"
        for (var entry : originalNames.keySet()) {
            originalNames.put(entry, entry.getImageName());
        }
        
        currentSublevels = sublevels;
        
        logger.info("Applied {} sublevels to {} project entries", sublevels, originalNames.size());
        
        closeStage();
    }
    
    @FXML
    private void onCancel() {
        // Restore original names
        for (var entry : originalNames.entrySet()) {
            entry.getKey().setImageName(entry.getValue());
        }
        
        // Force refresh of the project view
        var project = qupath.getProject();
        if (project != null) {
            qupath.refreshProject();
        }
        
        logger.info("Cancelled prefix operation, restored original names");
        closeStage();
    }
    
    private void closeStage() {
        Stage stage = (Stage) this.getScene().getWindow();
        stage.close();
    }
}