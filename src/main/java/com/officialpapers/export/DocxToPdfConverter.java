package com.officialpapers.export;

import java.io.IOException;
import java.nio.file.Path;

interface DocxToPdfConverter {

    Path convert(Path docxPath, Path outputDirectory) throws IOException;
}
