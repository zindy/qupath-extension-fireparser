package qupath.ext.fireparser.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for CellProfiler Pattern Tester with a live TableView preview.
 */
public class PatternController extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(PatternController.class);
    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.fireparser.ui.strings");
    
    // Color palette for capture groups, matching the CSS
    private static final String[] GROUP_COLORS = {
        "group-color-1", "group-color-2", "group-color-3",
        "group-color-4", "group-color-5", "group-color-6"
    };

    @FXML private CodeArea patternArea;
    @FXML private TextField convertedField;
    @FXML private TextField groupsField;
    @FXML private TableView<ImageMetadataEntry> resultsTable;
    @FXML private Button applyButton;
    @FXML private Button cancelButton;

    private final QuPathGUI qupath;
    private final List<ProjectImageEntry<?>> projectImages;

    public static PatternController createInstance(QuPathGUI qupath) throws IOException {
        return new PatternController(qupath);
    }

    private PatternController(QuPathGUI qupath) throws IOException {
        this.qupath = qupath;
        var project = qupath.getProject();
        if (project == null) {
            throw new IllegalStateException("No project is currently open");
        }
        // Use the double cast to resolve the type mismatch
        this.projectImages = (List<ProjectImageEntry<?>>)(List)project.getImageList();

        var url = PatternController.class.getResource("pattern.fxml");
        FXMLLoader loader = new FXMLLoader(url, resources);
        loader.setRoot(this);
        loader.setController(this);
        loader.load();
        
        // Set default pattern
        //String defaultPattern = "(?P<Plate>.*)_(?P<Well>[A-H][0-9]{2})_s(?P<Site>[0-9])_w(?P<Channel>[0-9])";
        String defaultPattern = "BBBC017_v1_images_(?P<Treatment>NIRHTa[+-])(?P<Plate>\\d{3})\\/(?P<Barcode>AS_\\d+_\\d+)_?(?P<Well>[A-Z]\\d{2})f(?P<Field>\\d+)\\.ome\\.tif";

        patternArea.replaceText(defaultPattern);
        
        // Add listener to update UI when the pattern changes
        patternArea.textProperty().addListener((obs, oldText, newText) -> updateUI());
        
        // Set up buttons
        applyButton.setOnAction(e -> onApply());
        cancelButton.setOnAction(e -> onCancel());

        // Apply CSS for syntax highlighting in the CodeArea
        applySyntaxHighlightingCSS();
        
        // Initial update
        updateUI();
    }

    /**
     * Main method to update all UI elements based on the current pattern.
     */
    private void updateUI() {
        String cpPattern = patternArea.getText();
        String javaPattern = convertPattern(cpPattern);
        
        // 1. Update pattern area syntax highlighting
        patternArea.setStyleSpans(0, computePatternHighlighting(cpPattern).create());
        
        // 2. Update converted and groups fields
        convertedField.setText(javaPattern);
        List<String> groups = extractGroupNames(javaPattern);
        groupsField.setText(groups.isEmpty() ? "No groups found" : String.join(", ", groups));
        
        // 3. Update the results TableView
        updateTableView(javaPattern, groups);
    }

    private void updateTableView(String javaPattern, List<String> groupNames) {
        resultsTable.getColumns().clear();
        
        if (projectImages.isEmpty()) {
            resultsTable.setPlaceholder(new Label("No images in project."));
            return;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(javaPattern);
        } catch (Exception e) {
            resultsTable.setPlaceholder(new Label("Invalid pattern: " + e.getMessage()));
            return;
        }

        // --- Create and define columns ---

        // Column 1: Image Name (with custom highlighting cell factory)
        TableColumn<ImageMetadataEntry, String> nameCol = new TableColumn<>("Image Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().imageNameProperty());
        nameCol.setCellFactory(col -> new HighlightedTableCell(pattern, groupNames));
        nameCol.setPrefWidth(200);
        resultsTable.getColumns().add(nameCol);

        // Subsequent columns: One for each extracted group
        for (String groupName : groupNames) {
            TableColumn<ImageMetadataEntry, String> col = new TableColumn<>(groupName);
            col.setCellValueFactory(cellData -> 
                new SimpleStringProperty(cellData.getValue().getMetadataValue(groupName))
            );
            resultsTable.getColumns().add(col);
        }

        // --- Populate data ---
        ObservableList<ImageMetadataEntry> data = FXCollections.observableArrayList();
        for (var entry : projectImages) {
            String imageName = entry.getImageName();
            Map<String, String> metadata = new HashMap<>();
            
            Matcher matcher = pattern.matcher(imageName);
            if (matcher.find()) {
                for (String group : groupNames) {
                    try {
                        metadata.put(group, matcher.group(group));
                    } catch (IllegalArgumentException e) {
                        // Group not found in this match
                        metadata.put(group, "");
                    }
                }
            }
            data.add(new ImageMetadataEntry(imageName, metadata));
        }
        resultsTable.setItems(data);
        resultsTable.setFixedCellSize(50); // Allows variable row heights
        /*
        resultsTable.setRowFactory(tv -> {
            TableRow<ImageMetadataEntry> row = new TableRow<>();
            row.setStyle("-fx-padding: 0;");
            return row;
        });
        */

    }

    /**
     * Custom TableCell to apply syntax highlighting to the image name.
     */
private class HighlightedTableCell extends TableCell<ImageMetadataEntry, String> {
    private final Pattern pattern;
    private final TextFlow textFlow = new TextFlow();

    public HighlightedTableCell(Pattern pattern, List<String> groupNames) {
        this.pattern = pattern;
        // Keep the textFlow compact to prevent huge rows
        this.textFlow.setMaxHeight(50); 
        this.textFlow.setStyle("-fx-background-color: transparent;"); 

    }

@Override
protected void updateItem(String imageName, boolean empty) {
    super.updateItem(imageName, empty);
    if (empty || imageName == null) {
        setGraphic(null);
        setText(null);
        return;
    }

    textFlow.getChildren().clear();
    Matcher matcher = pattern.matcher(imageName);

    if (matcher.find()) {
        int lastEnd = 0;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            int start = matcher.start(i);
            int end = matcher.end(i);
            if (start >= 0) {
                if (start > lastEnd) {
                    textFlow.getChildren().add(new Text(imageName.substring(lastEnd, start)));
                }
                Text groupText = new Text(imageName.substring(start, end));
                groupText.setStyle("-fx-fill: " + getHexColorForGroup(i - 1) + "; -fx-font-weight: bold;");
                textFlow.getChildren().add(groupText);
                lastEnd = end;
            }
        }
        if (lastEnd < imageName.length()) {
            textFlow.getChildren().add(new Text(imageName.substring(lastEnd)));
        }
    } else {
        textFlow.getChildren().add(new Text(imageName));
    }
    
    setGraphic(textFlow);
    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    
    // Let the table compute the proper height based on content
    //setPrefHeight(USE_COMPUTED_SIZE);
    //setMaxHeight(USE_COMPUTED_SIZE);
    //setMinHeight(USE_PREF_SIZE);
}
    private String getHexColorForGroup(int index) {
        String[] hexColors = {"#22c55e", "#f97316", "#a855f7", "#3b82f6", "#ec4899", "#14b8a6"};
        return hexColors[index % hexColors.length];
    }
}

 @FXML
private void onApply() {
    String cpPattern = patternArea.getText();
    String javaPattern = convertPattern(cpPattern);
    List<String> groupNames = extractGroupNames(javaPattern);
    
    if (groupNames.isEmpty()) {
        logger.warn("No capture groups found in pattern. No metadata to apply.");
        return;
    }
    
    Pattern pattern;
    try {
        pattern = Pattern.compile(javaPattern);
    } catch (Exception e) {
        logger.error("Invalid pattern: " + e.getMessage());
        return;
    }
    
    // Apply metadata to each image in the project
    int successCount = 0;
    int failCount = 0;
    
    for (ProjectImageEntry<?> entry : projectImages) {
        String imageName = entry.getImageName();
        Matcher matcher = pattern.matcher(imageName);
        // Get the metadata map and add/modify values
        var metadata = entry.getMetadata();
        
        if (matcher.find()) {
            // Extract metadata from the pattern
            for (String groupName : groupNames) {
                try {
                    String value = matcher.group(groupName);
                    if (value != null) {
                        // Store as image metadata
                        metadata.put(groupName, value);
                        successCount++;
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Group '{}' not found for image '{}'", groupName, imageName);
                    failCount++;
                }
            }
        } else {
            logger.warn("Pattern did not match image name: {}", imageName);
            failCount++;
        }
    }
    
    // Save the project to persist the metadata changes
    try {
        var project = qupath.getProject();
        if (project != null) {
            project.syncChanges();
            logger.info("Pattern '{}' applied successfully. {} metadata values added.", 
                       cpPattern, successCount);
            
            if (failCount > 0) {
                logger.warn("{} metadata extractions failed.", failCount);
            }
        }
    } catch (IOException e) {
        logger.error("Failed to save project changes: " + e.getMessage(), e);
    }
    
    closeStage();
}   
    @FXML
    private void onCancel() {
        closeStage();
    }
    
    private void closeStage() {
        Stage stage = (Stage) this.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    // --- The following methods are kept from the original PatternController ---
    // convertPattern, extractGroupNames, computePatternHighlighting, getStyleClass, applySyntaxHighlightingCSS
    // They are unchanged and essential for the CodeArea highlighting.
    
    private String convertPattern(String cpPattern) {
        return cpPattern.replaceAll("\\(\\?P<", "(?<");
    }

    private List<String> extractGroupNames(String pattern) {
        List<String> groupNames = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\(\\?<(\\w+)>").matcher(pattern);
        while (matcher.find()) {
            groupNames.add(matcher.group(1));
        }
        return groupNames;
    }

private StyleSpansBuilder<Collection<String>> computePatternHighlighting(String pattern) {
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    
    if (pattern == null || pattern.isEmpty()) {
        spansBuilder.add(Collections.singleton("plain"), 0);
        return spansBuilder;
    }

    // Matcher for capturing groups, special regex syntax, character classes, and quantifiers
    Matcher matcher = Pattern.compile(
            "(?<GROUP>\\(\\?P<\\w+>)|" +
            "(?<SYNTAX>[\\\\\\.\\^\\$\\*\\+\\?\\{\\}\\(\\)\\|\\[\\]])|" +
            "(?<CHARCLASS>\\[.*?\\])|" +
            "(?<QUANTIFIER>\\{.*?\\})"
    ).matcher(pattern);

    int lastKwEnd = 0;
    int groupCount = 0;
    
    while (matcher.find()) {
        String styleClass = "plain";
        if (matcher.group("GROUP") != null) {
            styleClass = GROUP_COLORS[groupCount % GROUP_COLORS.length];
            groupCount++;
        } else if (matcher.group("SYNTAX") != null) {
            styleClass = "regex-syntax";
        } else if (matcher.group("CHARCLASS") != null) {
            styleClass = "char-class";
        } else if (matcher.group("QUANTIFIER") != null) {
            styleClass = "quantifier";
        }

        spansBuilder.add(Collections.singleton("plain"), matcher.start() - lastKwEnd);
        spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
        lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.singleton("plain"), pattern.length() - lastKwEnd);
    return spansBuilder;
}
    private String getStyleClass(int index) {
        if (index == -1) return "plain";
        if (index == -2) return "regex-syntax";
        if (index == -3) return "char-class";
        if (index == -4) return "quantifier";
        return GROUP_COLORS[index];
    }

    private void applySyntaxHighlightingCSS() {
        String css = """
            .code-area {
                -fx-font-family: 'Consolas', 'Monaco', monospace;
                -fx-font-size: 12px;
            }
            .code-area .regex-syntax {
                -fx-fill: #808080;
                -fx-font-weight: bold;
            }
            .code-area .char-class {
                -fx-fill: #0066cc;
                -fx-font-weight: bold;
            }
            .code-area .quantifier {
                -fx-fill: #cc6600;
                -fx-font-weight: bold;
            }
            .code-area .plain {
                -fx-fill: #000000;
            }
            .code-area .group-color-1 {
                -fx-fill: #22c55e;
                -fx-font-weight: bold;
            }
            .code-area .group-color-2 {
                -fx-fill: #f97316;
                -fx-font-weight: bold;
            }
            .code-area .group-color-3 {
                -fx-fill: #a855f7;
                -fx-font-weight: bold;
            }
            .code-area .group-color-4 {
                -fx-fill: #3b82f6;
                -fx-font-weight: bold;
            }
            .code-area .group-color-5 {
                -fx-fill: #ec4899;
                -fx-font-weight: bold;
            }
            .code-area .group-color-6 {
                -fx-fill: #14b8a6;
                -fx-font-weight: bold;
            }
            """;
        
        try {
            String encoded = Base64.getEncoder().encodeToString(css.getBytes());
            this.getStylesheets().add("data:text/css;base64," + encoded);
        } catch (Exception e) {
            System.err.println("Could not apply CSS: " + e.getMessage());
        }
    }
}