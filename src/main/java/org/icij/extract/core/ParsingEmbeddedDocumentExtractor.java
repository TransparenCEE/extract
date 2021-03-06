package org.icij.extract.core;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.IOException;

import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.EncryptedDocumentException;

import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.SAXException;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

/**
 * A custom extractor that is an almost exact copy of Tika's default
 * extractor for embedded documents.
 *
 * Logs errors that Tika's default extractor otherwise swallows.
 *
 * @since 1.0.0-beta
 */
public class ParsingEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    private static final File ABSTRACT_PATH = new File("");
    private static final Parser DELEGATING_PARSER = new DelegatingParser();

	private final Logger logger;
	private final Path parent;
	private final ParseContext context;

	public ParsingEmbeddedDocumentExtractor(Logger logger, Path parent, ParseContext context) {
		this.logger = logger;
		this.parent = parent;
		this.context = context;
	}

	public boolean shouldParseEmbedded(Metadata metadata) {
		final DocumentSelector selector = context.get(DocumentSelector.class);
		if (selector != null) {
			return selector.select(metadata);
		}

		final FilenameFilter filter = context.get(FilenameFilter.class);
		if (filter != null) {
			final String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
			if (name != null) {
				return filter.accept(ABSTRACT_PATH, name);
			}
		}

		return true;
	}

	public void parseEmbedded(InputStream input, ContentHandler handler, Metadata metadata, boolean outputHtml)
		throws SAXException, IOException {
		if (outputHtml) {
			final AttributesImpl attributes = new AttributesImpl();
			attributes.addAttribute("", "class", "class", "CDATA", "package-entry");
			handler.startElement(XHTML, "div", "div", attributes);
		}

		final String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
		if (name != null && name.length() > 0 && outputHtml) {
			handler.startElement(XHTML, "h1", "h1", new AttributesImpl());
			char[] chars = name.toCharArray();
			handler.characters(chars, 0, chars.length);
			handler.endElement(XHTML, "h1", "h1");
		}

		// Use the delegate parser to parse this entry.
		try (
			final TemporaryResources tmp = new TemporaryResources()
		) {
			final TikaInputStream newStream = TikaInputStream.get(new CloseShieldInputStream(input), tmp);
			if (input instanceof TikaInputStream) {
				final Object container = ((TikaInputStream) input).getOpenContainer();
				if (container != null) {
					newStream.setOpenContainer(container);
				}
			}

			DELEGATING_PARSER.parse(newStream, new EmbeddedContentHandler(new BodyContentHandler(handler)), metadata, context);
		} catch (EncryptedDocumentException e) {
			logger.log(Level.SEVERE, "Encrypted document embedded in document: " + parent + ".", e);
		} catch (TikaException e) {
			logger.log(Level.SEVERE, "Unable to parse embedded document in document: " + parent + ".", e);
		}

		if (outputHtml) {
			handler.endElement(XHTML, "div", "div");
		}
	}
}
