package alien4cloud.documentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovyjarjarantlr.StringUtils;
import io.github.robwin.markup.builder.MarkupLanguage;
import io.github.robwin.swagger2markup.Swagger2MarkupConverter;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Custom markdown generation to be easily included in alien 4 cloud documentation.
 */
public class AlienSwagger2MarkupConverter {
    private final Swagger swagger;
    private final MarkupLanguage markupLanguage;
    private final String examplesFolderPath;
    private final String schemasFolderPath;
    private final String descriptionsFolderPath;
    private final boolean separatedDefinitions;
    private static final String OVERVIEW_DOCUMENT = "overview";
    private static final String CONTROLLER_DOCUMENT = "controller_";
    private static final String DEFINITIONS_DOCUMENT = "definitions";

    /**
     * @param markupLanguage the markup language which is used to generate the files
     * @param swagger the Swagger object
     * @param examplesFolderPath the folderPath where examples are stored
     * @param schemasFolderPath the folderPath where (XML, JSON)-Schema files are stored
     * @param descriptionsFolderPath the folderPath where descriptions are stored
     * @param separatedDefinitions create separate definition files for each model definition.
     */
    AlienSwagger2MarkupConverter(MarkupLanguage markupLanguage, Swagger swagger, String examplesFolderPath, String schemasFolderPath,
            String descriptionsFolderPath, boolean separatedDefinitions) {
        this.markupLanguage = markupLanguage;
        this.swagger = swagger;
        this.examplesFolderPath = examplesFolderPath;
        this.schemasFolderPath = schemasFolderPath;
        this.descriptionsFolderPath = descriptionsFolderPath;
        this.separatedDefinitions = separatedDefinitions;
    }

    /**
     * Creates a Swagger2MarkupConverter.Builder using a given Swagger source.
     *
     * @param swaggerLocation the Swagger location. Can be a HTTP url or a path to a local file.
     * @return a Swagger2MarkupConverter
     */
    public static Builder from(String swaggerLocation) {
        Validate.notEmpty(swaggerLocation, "swaggerLocation must not be empty!");
        return new Builder(swaggerLocation);
    }

    /**
     * Creates a Swagger2MarkupConverter.Builder from a given Swagger model.
     *
     * @param swagger the Swagger source.
     * @return a Swagger2MarkupConverter
     */
    public static Builder from(Swagger swagger) {
        Validate.notNull(swagger, "swagger must not be null!");
        return new Builder(swagger);
    }

    /**
     * Creates a Swagger2MarkupConverter.Builder from a given Swagger YAML or JSON String.
     *
     * @param swagger the Swagger YAML or JSON String.
     * @return a Swagger2MarkupConverter
     * @throws java.io.IOException if String can not be parsed
     */
    public static Builder fromString(String swagger) throws IOException {
        Validate.notEmpty(swagger, "swagger must not be null!");
        ObjectMapper mapper;
        if (swagger.trim().startsWith("{")) {
            mapper = Json.mapper();
        } else {
            mapper = Yaml.mapper();
        }
        JsonNode rootNode = mapper.readTree(swagger);

        // must have swagger node set
        JsonNode swaggerNode = rootNode.get("swagger");
        if (swaggerNode == null)
            throw new IllegalArgumentException("Swagger String is in the wrong format");

        return new Builder(mapper.convertValue(rootNode, Swagger.class));
    }

    /**
     * Builds the document with the given markup language and stores
     * the files in the given folder.
     *
     * @param targetFolderPath the target folder
     * @throws IOException if the files cannot be written
     */
    public void intoFolder(String targetFolderPath) throws IOException {
        Validate.notEmpty(targetFolderPath, "folderPath must not be null!");
        buildDocuments(targetFolderPath);
    }

    /**
     * Builds all documents and writes them to a directory
     *
     * @param directory the directory where the generated file should be stored
     * @throws IOException if a file cannot be written
     */
    private void buildDocuments(String directory) throws IOException {
        new OverviewDocument(swagger, markupLanguage).build().writeToFile(directory, OVERVIEW_DOCUMENT, StandardCharsets.UTF_8);

        List<Tag> tags = swagger.getTags();
        Map<String, Controller> controllerMap = Maps.newHashMap();
        for (Tag tag : tags) {
            String ctrlDesc = org.apache.commons.lang3.StringUtils.isBlank(tag.getDescription()) ? tag.getName() : tag.getDescription();
            controllerMap.put(tag.getName(), new Controller(ctrlDesc));
        }

        Map<String, Path> paths = swagger.getPaths();
        if (MapUtils.isNotEmpty(paths)) {
            for (Map.Entry<String, Path> entry : paths.entrySet()) {
                Path path = entry.getValue();
                if (path.getOperations().size() > 0 && path.getOperations().get(0).getTags().size() > 0) {
                    String controller = path.getOperations().get(0).getTags().get(0);
                    controllerMap.get(controller).controllerPaths.add(entry);
                }
            }
        }

        for (Map.Entry<String, Controller> controllerEntry : controllerMap.entrySet()) {
            new ControllerDocument(swagger, controllerEntry, markupLanguage, examplesFolderPath).build().writeToFile(directory,
                    CONTROLLER_DOCUMENT + controllerEntry.getKey(), StandardCharsets.UTF_8);
        }

        // new PathsDocument(swagger, markupLanguage, examplesFolderPath, descriptionsFolderPath).build().writeToFile(directory, PATHS_DOCUMENT,
        // StandardCharsets.UTF_8);
        // new DefinitionsDocument(swagger, markupLanguage, schemasFolderPath, descriptionsFolderPath, separatedDefinitions, directory).build()
        // .writeToFile(directory, DEFINITIONS_DOCUMENT, StandardCharsets.UTF_8);
    }

    public class Controller {
        String description;
        List<Map.Entry<String, Path>> controllerPaths;

        public Controller(String description) {
            this.description = description;
            controllerPaths = Lists.newArrayList();
        }
    }

    public static class Builder {
        private final Swagger swagger;
        private String examplesFolderPath;
        private String schemasFolderPath;
        private String descriptionsFolderPath;
        private boolean separatedDefinitions;
        private MarkupLanguage markupLanguage = MarkupLanguage.ASCIIDOC;

        /**
         * Creates a Builder using a given Swagger source.
         *
         * @param swaggerLocation the Swagger location. Can be a HTTP url or a path to a local file.
         */
        Builder(String swaggerLocation) {
            swagger = new SwaggerParser().read(swaggerLocation);
            if (swagger == null) {
                throw new IllegalArgumentException("Failed to read the Swagger file. ");
            }
        }

        /**
         * Creates a Builder using a given Swagger model.
         *
         * @param swagger the Swagger source.
         */
        Builder(Swagger swagger) {
            this.swagger = swagger;
        }

        public AlienSwagger2MarkupConverter build() {
            return new AlienSwagger2MarkupConverter(markupLanguage, swagger, examplesFolderPath, schemasFolderPath, descriptionsFolderPath,
                    separatedDefinitions);
        }

        /**
         * Specifies the markup language which should be used to generate the files
         *
         * @param markupLanguage the markup language which is used to generate the files
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder withMarkupLanguage(MarkupLanguage markupLanguage) {
            this.markupLanguage = markupLanguage;
            return this;
        }

        /**
         * Include hand-written descriptions into the Paths and Definitions document
         *
         * @param descriptionsFolderPath the path to the folder where the description documents reside
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder withDescriptions(String descriptionsFolderPath) {
            this.descriptionsFolderPath = descriptionsFolderPath;
            return this;
        }

        /**
         * In addition to the definitions file, also create separate definition files for each model definition.
         *
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder withSeparatedDefinitions() {
            this.separatedDefinitions = true;
            return this;
        }

        /**
         * Include examples into the Paths document
         *
         * @param examplesFolderPath the path to the folder where the example documents reside
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder withExamples(String examplesFolderPath) {
            this.examplesFolderPath = examplesFolderPath;
            return this;
        }

        /**
         * Include (JSON, XML) schemas into the Definitions document
         *
         * @param schemasFolderPath the path to the folder where the schema documents reside
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder withSchemas(String schemasFolderPath) {
            this.schemasFolderPath = schemasFolderPath;
            return this;
        }

        /**
         * Customize the Swagger data in any useful way
         *
         * @param preProcessor function object to mutate the swagger object
         * @return the Swagger2MarkupConverter.Builder
         */
        public Builder preProcessSwagger(Consumer<Swagger> preProcessor) {
            preProcessor.accept(this.swagger);
            return this;
        }
    }
}