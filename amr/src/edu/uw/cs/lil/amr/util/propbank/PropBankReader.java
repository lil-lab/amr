package edu.uw.cs.lil.amr.util.propbank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import edu.cornell.cs.nlp.utils.log.ILogger;
import edu.cornell.cs.nlp.utils.log.LoggerFactory;

public class PropBankReader extends DefaultHandler {
	public static final ILogger				LOG	= LoggerFactory
														.create(PropBankReader.class);
	private PropBankFrame					currentFrame;
	private PropBankPredicate				currentPredicate;
	private String							currentRoleDescription;
	private int								currentRoleIndex;
	private String							currentRoleType;
	private final List<PropBankPredicate>	predicates;
	private final SAXParser					sp;

	public PropBankReader() {
		try {
			sp = SAXParserFactory.newInstance().newSAXParser();
		} catch (final SAXException | ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		predicates = new LinkedList<>();
	}

	public static List<PropBankPredicate> of(Path path) {
		final PropBankReader r = new PropBankReader();
		try {
			r.sp.parse(path.toUri().toString(), r);
		} catch (final SAXException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return r.predicates;
	}

	private static Integer getInteger(String i) {
		if (i == null) {
			return null;
		}
		try {
			return Integer.parseInt(i);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	@Override
	public void characters(char[] buffer, int offset, int length) {
		// Nothing to do
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if (qName.equals("role")) {
			currentFrame.put(currentRoleIndex, new PropBankRole(
					currentRoleIndex, currentRoleDescription, currentRoleType));
		}
		if (qName.equals("roleset")) {
			currentPredicate.add(currentFrame);
		}
		if (qName.equals("predicate")) {
			predicates.add(currentPredicate);
		}
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if (qName.equals("predicate")) {
			currentPredicate = new PropBankPredicate();
		}
		if (qName.equals("roleset")) {
			currentFrame = new PropBankFrame(attributes.getValue("id"));
		}
		if (qName.equals("role")) {
			final Integer roleIndex = getInteger(attributes.getValue("n"));
			if (roleIndex != null) {
				currentRoleIndex = roleIndex;
				currentRoleDescription = attributes.getValue("descr");
			}
		}
		if (qName.equals("vnrole")) {
			currentRoleType = attributes.getValue("vntheta");
		}
	}
}