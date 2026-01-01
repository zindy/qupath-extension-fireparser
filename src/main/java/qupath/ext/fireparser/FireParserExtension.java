package qupath.ext.fireparser;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.fireparser.ui.PatternController;
import qupath.ext.fireparser.ui.PrefixController;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import java.io.IOException;
import java.util.ResourceBundle;


/**
 * This extension adds filename / folder parsing capability
 */
public class FireParserExtension implements QuPathExtension {
	/**
	 * A resource bundle containing all the text used by the extension. This may be useful for translation to other languages.
	 * Note that this is optional and you can define the text within the code and FXML files that you use.
	 */
	private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.fireparser.ui.strings");
	private static final Logger logger = LoggerFactory.getLogger(FireParserExtension.class);

	/**
	 * Display name for your extension
	 */
	private static final String EXTENSION_NAME = resources.getString("name");

	/**
	 * Short description, used under 'Extensions > Installed extensions'
	 */
	private static final String EXTENSION_DESCRIPTION = resources.getString("description");

	/**
	 * QuPath version that the extension is designed to work with.
	 * This allows QuPath to inform the user if it seems to be incompatible.
	 */
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.5.0");

	/**
	 * Flag whether the extension is already installed (might not be needed... but we'll do it anyway)
	 */
	private boolean isInstalled = false;

	/**
	 * A 'persistent preference' - showing how to create a property that is stored whenever QuPath is closed.
	 * This preference will be managed in the main QuPath GUI preferences window.
	 */
	private static final BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);

	/**
	 * Another 'persistent preference'.
	 * This one will be managed using a GUI element created by the extension.
	 * We use {@link Property<Integer>} rather than {@link IntegerProperty}
	 * because of the type of GUI element we use to manage it.
	 */
	private static final Property<Integer> integerOption = PathPrefs.createPersistentPreference(
			"demo.num.option", 1).asObject();

	/**
	 * An example of how to expose persistent preferences to other classes in your extension.
	 * @return The persistent preference, so that it can be read or set somewhere else.
	 */
	public static Property<Integer> integerOptionProperty() {
		return integerOption;
	}

	/**
	 * Create stages for the extension to display
	 */
	private Stage patternStage;
	private Stage prefixStage;

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addPreferenceToPane(qupath);
		addMenuItems(qupath);
	}

	/**
	 * FireParser preferences
	 * The preference will be in a section of the preference pane based on the
	 * category you set. The description is used as a tooltip.
	 * @param qupath The currently running QuPathGUI instance.
	 */
	private void addPreferenceToPane(QuPathGUI qupath) {
        var propertyItem = new PropertyItemBuilder<>(enableExtensionProperty, Boolean.class)
				.name(resources.getString("menu.enableCellProfilerCompat"))
				.category("FireParser extension")
				.description("Make the Regex Parser compatible with CellProfiler patterns")
				.build();
		qupath.getPreferencePane()
				.getPropertySheet()
				.getItems()
				.add(propertyItem);
	}


	/**
	 * FireParser showing how new commands can be added to a QuPath menu.
	 * @param qupath The QuPath GUI
	 */
	private void addMenuItems(QuPathGUI qupath) {
		Menu menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		
		// Prefix manager menu item
		MenuItem prefixMenuItem = new MenuItem(resources.getString("menu.prefix.manager"));
		prefixMenuItem.setOnAction(e -> createPrefixStage(qupath));
		prefixMenuItem.disableProperty().bind(enableExtensionProperty.not());
		
		// Pattern tester menu item
		MenuItem patternMenuItem = new MenuItem(resources.getString("menu.pattern.tester"));
		patternMenuItem.setOnAction(e -> createPatternStage(qupath));
		patternMenuItem.disableProperty().bind(enableExtensionProperty.not());
		
		menu.getItems().addAll(prefixMenuItem, patternMenuItem);
	}

	/**
	 * FireParser showing how to create a new stage with a JavaFX FXML interface.
	 */
	private void createPatternStage(QuPathGUI qupath) {
		if (patternStage == null) {
			try {
				patternStage = new Stage();
				patternStage.setTitle(resources.getString("stage.pattern.title"));

				PatternController controller = PatternController.createInstance(qupath);
				Scene scene = new Scene(controller, 700, 850);

				patternStage.setScene(scene);
				patternStage.setResizable(true);
				patternStage.show();
			} catch (IOException e) {
				Dialogs.showErrorMessage(resources.getString("error"), resources.getString("error.gui-loading-failed"));
				logger.error("Unable to load pattern tester interface FXML", e);
			}
		}
		patternStage.show();
	}
	
	/**
	 * Create the prefix manager stage
	 */
	private void createPrefixStage(QuPathGUI qupath) {
		try {
			// Create new stage each time (don't reuse to ensure fresh data)
			prefixStage = new Stage();
			prefixStage.setTitle(resources.getString("stage.prefix.title"));
			
			PrefixController controller = PrefixController.createInstance(qupath);
			Scene scene = new Scene(controller, 500, 300);
			
			prefixStage.setScene(scene);
			//prefixStage.setResizable(false);
			prefixStage.sizeToScene();
			prefixStage.show();
		} catch (IllegalStateException e) {
			Dialogs.showErrorMessage(resources.getString("error"), e.getMessage());
			logger.error("Cannot open prefix manager", e);
		} catch (IOException e) {
			Dialogs.showErrorMessage(resources.getString("error"), resources.getString("error.gui-loading-failed"));
			logger.error("Unable to load prefix manager interface FXML", e);
		}
	}


	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}
}