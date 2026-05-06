package com.syuuk.patentflow.patent.client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

final class PatentLookupXmlParser {

    private PatentLookupXmlParser() {
    }

    static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("외부 특허 XML 응답을 파싱할 수 없습니다.", exception);
        }
    }

    static Optional<String> text(Document document, String... tagNames) {
        return Arrays.stream(tagNames)
                .map(document::getElementsByTagName)
                .filter(nodes -> nodes.getLength() > 0)
                .map(nodes -> nodes.item(0))
                .map(node -> node.getTextContent().trim())
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    static Optional<String> firstText(Document document, String parentTagName, String... tagNames) {
        NodeList parents = document.getElementsByTagName(parentTagName);
        if (parents.getLength() == 0) {
            return Optional.empty();
        }
        Document scopedDocument = document;
        return text(scopedDocument, tagNames);
    }

    static LocalDate date(Document document, String... tagNames) {
        return text(document, tagNames)
                .map(PatentLookupXmlParser::parseDate)
                .orElse(null);
    }

    private static LocalDate parseDate(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 8) {
            return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.parse(value);
    }
}
