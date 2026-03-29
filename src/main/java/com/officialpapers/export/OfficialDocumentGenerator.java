package com.officialpapers.export;

import java.io.IOException;

interface OfficialDocumentGenerator {

    GeneratedFile generate(OfficialDocumentData data) throws IOException;
}
