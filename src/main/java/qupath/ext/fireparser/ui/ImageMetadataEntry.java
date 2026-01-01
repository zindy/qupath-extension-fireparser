package qupath.ext.fireparser.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.util.Map;

/**
 * Helper class to represent a row in the metadata TableView.
 */
public class ImageMetadataEntry {
    private final StringProperty imageName;
    private final Map<String, String> metadata;

    public ImageMetadataEntry(String imageName, Map<String, String> metadata) {
        this.imageName = new SimpleStringProperty(imageName);
        this.metadata = metadata;
    }

    public StringProperty imageNameProperty() {
        return imageName;
    }

    public String getImageName() {
        return imageName.get();
    }

    public String getMetadataValue(String key) {
        return metadata.getOrDefault(key, "");
    }
}