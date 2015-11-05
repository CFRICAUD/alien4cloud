/*
 *
 *  Copyright 2015 Robert Winkler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package alien4cloud.documentation;

import io.swagger.models.Swagger;
import io.github.robwin.markup.builder.MarkupDocBuilder;
import io.github.robwin.markup.builder.MarkupDocBuilders;
import io.github.robwin.markup.builder.MarkupLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Robert Winkler
 */
public abstract class MarkupDocument {

    protected static final String DELIMITER = "|";
    protected static final String DEFAULT_COLUMN = "Default";
    protected static final String REQUIRED_COLUMN = "Required";
    protected static final String SCHEMA_COLUMN = "Schema";
    protected static final String NAME_COLUMN = "Name";
    protected static final String DESCRIPTION_COLUMN = "Description";
    protected static final String DESCRIPTION = DESCRIPTION_COLUMN;
    protected static final String PRODUCES = "Produces";
    protected static final String CONSUMES = "Consumes";
    protected static final String TAGS = "Tags";
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected Swagger swagger;
    protected MarkupLanguage markupLanguage;
    protected MarkupDocBuilder markupDocBuilder;

    MarkupDocument(Swagger swagger, MarkupLanguage markupLanguage) {
        this.swagger = swagger;
        this.markupLanguage = markupLanguage;
        this.markupDocBuilder = MarkupDocBuilders.documentBuilder(markupLanguage);
    }

    /**
     * Builds the MarkupDocument.
     *
     * @return the built MarkupDocument
     * @throws IOException if the files to include are not readable
     */
    public abstract MarkupDocument build() throws IOException;

    /**
     * Returns a string representation of the document.
     */
    public String toString() {
        return markupDocBuilder.toString();
    }

    /**
     * Writes the content of the builder to a file and clears the builder.
     *
     * @param directory the directory where the generated file should be stored
     * @param fileName the name of the file
     * @param charset the the charset to use for encoding
     * @throws IOException if the file cannot be written
     */
    public void writeToFile(String directory, String fileName, Charset charset) throws IOException {
        markupDocBuilder.writeToFile(directory, fileName, charset);
    }
}
