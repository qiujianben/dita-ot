/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.writer;

import static org.dita.dost.util.Constants.*;
import static java.util.Arrays.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.dita.dost.exception.DITAOTXMLErrorHandler;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.module.Content;
import org.dita.dost.reader.MapMetaReader;
import org.dita.dost.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/*
 * Created on 2011-04-14
 */

/**
 * 
 * @author Jian Le Shen
 */
public final class DitaMapMetaWriter extends AbstractXMLWriter {
    private String firstMatchTopic;
    private String lastMatchTopic;
    private Hashtable<String, Node> metaTable;
    /** topic path that topicIdList need to match */
    private List<String> matchList;
    private boolean needResolveEntity;
    private Writer output;
    private OutputStreamWriter ditaFileOutput;
    private StringWriter strOutput;
    private final XMLReader reader;
    /** whether to insert links at this topic */
    private boolean startMap;
    /** whether to cache the current stream into a buffer for building DOM tree */
    private boolean startDOM;
    /** whether metadata has been written */
    private boolean hasWritten;
    /** array list that is used to keep the hierarchy of topic id */
    private final List<String> topicIdList;
    private boolean insideCDATA;
    private final ArrayList<String> topicSpecList;

    private static final Hashtable<String, List<String>> moveTable;
    static{
        moveTable = new Hashtable<String, List<String>>(INT_32);
        moveTable.put(MAP_SEARCHTITLE.matcher, asList(MAP_TOPICMETA.localName, MAP_SEARCHTITLE.localName));
        moveTable.put(TOPIC_AUDIENCE.matcher, asList(MAP_TOPICMETA.localName, TOPIC_AUDIENCE.localName));
        moveTable.put(TOPIC_AUTHOR.matcher, asList(MAP_TOPICMETA.localName, TOPIC_AUTHOR.localName));
        moveTable.put(TOPIC_CATEGORY.matcher, asList(MAP_TOPICMETA.localName, TOPIC_CATEGORY.localName));
        moveTable.put(TOPIC_COPYRIGHT.matcher, asList(MAP_TOPICMETA.localName, TOPIC_COPYRIGHT.localName));
        moveTable.put(TOPIC_CRITDATES.matcher, asList(MAP_TOPICMETA.localName, TOPIC_CRITDATES.localName));
        moveTable.put(TOPIC_DATA.matcher, asList(MAP_TOPICMETA.localName, TOPIC_DATA.localName));
        moveTable.put(TOPIC_DATA_ABOUT.matcher, asList(MAP_TOPICMETA.localName, TOPIC_DATA_ABOUT.localName));
        moveTable.put(TOPIC_FOREIGN.matcher, asList(MAP_TOPICMETA.localName, TOPIC_FOREIGN.localName));
        moveTable.put(TOPIC_KEYWORDS.matcher, asList(MAP_TOPICMETA.localName, TOPIC_KEYWORDS.localName));
        moveTable.put(TOPIC_OTHERMETA.matcher, asList(MAP_TOPICMETA.localName, TOPIC_OTHERMETA.localName));
        moveTable.put(TOPIC_PERMISSIONS.matcher, asList(MAP_TOPICMETA.localName, TOPIC_PERMISSIONS.localName));
        moveTable.put(TOPIC_PRODINFO.matcher, asList(MAP_TOPICMETA.localName, TOPIC_PRODINFO.localName));
        moveTable.put(TOPIC_PUBLISHER.matcher, asList(MAP_TOPICMETA.localName, TOPIC_PUBLISHER.localName));
        moveTable.put(TOPIC_RESOURCEID.matcher, asList(MAP_TOPICMETA.localName, TOPIC_RESOURCEID.localName));
        moveTable.put(TOPIC_SOURCE.matcher, asList(MAP_TOPICMETA.localName, TOPIC_SOURCE.localName));
        moveTable.put(TOPIC_UNKNOWN.matcher, asList(MAP_TOPICMETA.localName, TOPIC_UNKNOWN.localName));
    }

    private static final Set<String> uniqueSet = MapMetaReader.uniqueSet;

    private static final Hashtable<String, Integer> compareTable;

    static{
        compareTable = new Hashtable<String, Integer>(INT_32);
        compareTable.put(MAP_TOPICMETA.localName, 1);
        compareTable.put(TOPIC_SEARCHTITLE.localName, 2);
        compareTable.put(TOPIC_SHORTDESC.localName, 3);
        compareTable.put(TOPIC_AUTHOR.localName, 4);
        compareTable.put(TOPIC_SOURCE.localName, 5);
        compareTable.put(TOPIC_PUBLISHER.localName, 6);
        compareTable.put(TOPIC_COPYRIGHT.localName, 7);
        compareTable.put(TOPIC_CRITDATES.localName, 8);
        compareTable.put(TOPIC_PERMISSIONS.localName, 9);
        compareTable.put(TOPIC_AUDIENCE.localName, 10);
        compareTable.put(TOPIC_CATEGORY.localName, 11);
        compareTable.put(TOPIC_KEYWORDS.localName, 12);
        compareTable.put(TOPIC_PRODINFO.localName, 13);
        compareTable.put(TOPIC_OTHERMETA.localName, 14);
        compareTable.put(TOPIC_RESOURCEID.localName, 15);
        compareTable.put(TOPIC_DATA.localName, 16);
        compareTable.put(TOPIC_DATA_ABOUT.localName, 17);
        compareTable.put(TOPIC_FOREIGN.localName, 18);
        compareTable.put(TOPIC_UNKNOWN.localName, 19);
    }



    /**
     * Default constructor of DitaIndexWriter class.
     */
    public DitaMapMetaWriter() {
        super();
        topicIdList = new ArrayList<String>(INT_16);
        topicSpecList = new ArrayList<String>(INT_16);

        metaTable = null;
        matchList = null;
        needResolveEntity = false;
        output = null;
        startMap = false;
        insideCDATA = false;

        try {
            reader = StringUtils.getXMLReader();
            reader.setContentHandler(this);
            reader.setProperty(LEXICAL_HANDLER_PROPERTY,this);
            reader.setFeature(FEATURE_NAMESPACE_PREFIX, true);
            //Edited by william on 2009-11-8 for ampbug:2893664 start
            reader.setFeature("http://apache.org/xml/features/scanner/notify-char-refs", true);
            reader.setFeature("http://apache.org/xml/features/scanner/notify-builtin-refs", true);
            //Edited by william on 2009-11-8 for ampbug:2893664 end
        } catch (final Exception e) {
            throw new RuntimeException("Failed to initialize XML parser: " + e.getMessage(), e);
        }

    }


    @Override
    public void characters(final char[] ch, final int start, final int length)
            throws SAXException {
        if(needResolveEntity){
            try {
                if(insideCDATA) {
                    output.write(ch, start, length);
                } else {
                    output.write(StringUtils.escapeXML(ch, start, length));
                }
            } catch (final Exception e) {
                logger.logException(e);
            }
        }
    }

    /** 
     * Check whether the hierarchy of current node match the matchList.
     */
    private boolean checkMatch() {
        if (matchList == null){
            return true;
        }
        final int matchSize = matchList.size();
        final int ancestorSize = topicIdList.size();
        final List<String> tail = topicIdList.subList(ancestorSize - matchSize, ancestorSize);
        return matchList.equals(tail);
    }

    @Override
    public void endCDATA() throws SAXException {
        insideCDATA = false;
        try{
            output.write(CDATA_END);
        }catch(final Exception e){
            logger.logException(e);
        }
    }

    @Override
    public void endDocument() throws SAXException {

        try {
            output.flush();
        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName)
            throws SAXException {
        if (!startMap){
            topicIdList.remove(topicIdList.size() - 1);
        }

        try {
            if (startMap && topicSpecList.contains(qName)){
                if (startDOM){
                    startDOM = false;
                    output.write("</map>");
                    output = ditaFileOutput;
                    processDOM();
                }else if (!hasWritten){
                    output = ditaFileOutput;
                    processDOM();
                }
            }

            output.write(LESS_THAN + SLASH + qName
                    + GREATER_THAN);


        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    private void processDOM() {
        try{
            final DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc;

            if (strOutput.getBuffer().length() > 0){
                builder.setErrorHandler(new DITAOTXMLErrorHandler(strOutput.toString(), logger));
                doc = builder.parse(new InputSource(new StringReader(strOutput.toString())));
            }else {
                doc = builder.newDocument();
                doc.appendChild(doc.createElement("map"));
            }

            final Node root = doc.getDocumentElement();

            final Iterator<Map.Entry<String, Node>> iter = metaTable.entrySet().iterator();

            while (iter.hasNext()){
                final Map.Entry<String, Node> entry = iter.next();
                moveMeta(entry,root);
            }

            outputMeta(root);

        } catch (final Exception e){
            logger.logException(e);
        }
        hasWritten = true;
    }


    private void outputMeta(final Node root) throws IOException {
        final NodeList children = root.getChildNodes();
        Node child = null;
        for (int i = 0; i<children.getLength(); i++){
            child = children.item(i);
            switch (child.getNodeType()){
            case Node.TEXT_NODE:
                output((Text) child); break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                output((ProcessingInstruction) child); break;
            case Node.ELEMENT_NODE:
                output((Element) child); break;
            }
        }

    }

    private void output(final ProcessingInstruction instruction) throws IOException{
        output.write("<?"+instruction.getTarget()+" "+instruction.getData()+"?>");
    }


    private void output(final Text text) throws IOException{
        output.write(StringUtils.escapeXML(text.getData()));
    }


    private void output(final Element elem) throws IOException{
        output.write("<"+elem.getNodeName());
        final NamedNodeMap attrMap = elem.getAttributes();
        for (int i = 0; i<attrMap.getLength(); i++){
            //edited on 2010-08-04 for bug:3038941 start
            //get node name
            final String nodeName = attrMap.item(i).getNodeName();
            //escape entity to avoid entity resolving
            final String nodeValue = StringUtils.escapeXML(attrMap.item(i).getNodeValue());
            //write into target file
            output.write(" "+ nodeName
                    +"=\""+ nodeValue
                    +"\"");
            //edited on 2010-08-04 for bug:3038941 end
        }
        output.write(">");
        final NodeList children = elem.getChildNodes();
        Node child;
        for (int j = 0; j<children.getLength(); j++){
            child = children.item(j);
            switch (child.getNodeType()){
            case Node.TEXT_NODE:
                output((Text) child); break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                output((ProcessingInstruction) child); break;
            case Node.ELEMENT_NODE:
                output((Element) child); break;
            }
        }

        output.write("</"+elem.getNodeName()+">");
    }

    private void moveMeta(final Entry<String, Node> entry, final Node root) {
        final List<String> metaPath = moveTable.get(entry.getKey());
        if (metaPath == null){
            // for the elements which doesn't need to be moved to topic
            // the processor need to neglect them.
            return;
        }
        final Iterator<String> token = metaPath.iterator();
        Node parent = null;
        Node child = root;
        Node current = null;
        Node item = null;
        NodeList childElements;
        boolean createChild = false;

        while (token.hasNext()){// find the element, if cannot find create one.
            final String next = token.next();
            parent = child;
            final Integer nextIndex = compareTable.get(next);
            Integer currentIndex = null;
            childElements = parent.getChildNodes();
            for (int i = 0; i < childElements.getLength(); i++){
                String name = null;
                String classValue = null;
                current = childElements.item(i);
                if (current.getNodeType() == Node.ELEMENT_NODE){
                    name = current.getNodeName();
                    classValue = ((Element)current).getAttribute(ATTRIBUTE_NAME_CLASS);
                }


                if ((name != null && current.getNodeName().equals(next))||(classValue != null&&(classValue.indexOf(next)!=-1))){
                    child = current;
                    break;
                } else if (name != null){
                    currentIndex = compareTable.get(name);
                    if (currentIndex == null){
                        // if compareTable doesn't contains the number for current name
                        // change to generalized element name to search again
                        String generalizedName = classValue.substring(classValue.indexOf(SLASH)+1);
                        generalizedName = generalizedName.substring(0, generalizedName.indexOf(STRING_BLANK));
                        currentIndex = compareTable.get(generalizedName);
                    }
                    if(currentIndex==null){
                        // if there is no generalized tag corresponding this tag
                        final Properties prop=new Properties();
                        prop.put("%1", name);
                        logger.logError(MessageUtils.getMessage("DOTJ038E", prop).toString());
                        break;
                    }
                    if(currentIndex.compareTo(nextIndex) > 0){
                        // if currentIndex > nextIndex
                        // it means we have passed to location to insert
                        // and we don't need to go to following child nodes
                        break;
                    }
                }
            }

            if (child==parent){
                // if there is no such child under current element,
                // create one
                child = parent.getOwnerDocument().createElement(next);
                ((Element)child).setAttribute(ATTRIBUTE_NAME_CLASS,"- map/"+next+" ");

                if (current == null ||
                        currentIndex == null ||
                        nextIndex.compareTo(currentIndex)>= 0){
                    parent.appendChild(child);
                    current = null;
                }else {
                    parent.insertBefore(child, current);
                    current = null;
                }

                createChild = true;
            }
        }

        // the root element of entry value is "stub"
        // there isn't any types of node other than Element under "stub"
        // when it is created. Therefore, the item here doesn't need node
        // type check.
        final NodeList list = entry.getValue().getChildNodes();
        for (int i = 0; i < list.getLength(); i++){
            item = list.item(i);
            if ((i == 0 && createChild) || uniqueSet.contains(entry.getKey()) ){
                item = parent.getOwnerDocument().importNode(item,true);
                parent.replaceChild(item, child);
                child = item; // prevent insert action still want to operate child after it is removed.
            } else {
                item = parent.getOwnerDocument().importNode(item,true);
                ((Element) parent).insertBefore(item, child);
            }
        }

    }


    @Override
    public void endEntity(final String name) throws SAXException {
        if(!needResolveEntity){
            needResolveEntity = true;
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length)
            throws SAXException {
        try {
            output.write(ch, start, length);
        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data)
            throws SAXException {
        String pi;
        try {
            pi = (data != null) ? target + STRING_BLANK + data : target;
            output.write(LESS_THAN + QUESTION
                    + pi + QUESTION + GREATER_THAN);
        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    /**
     * @param content value {@code Hashtable<String, Node>}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void setContent(final Content content) {
        metaTable = (Hashtable<String, Node>) content.getValue();
        if (metaTable == null) {
            throw new IllegalArgumentException("Content value must be non-null Hashtable<String, Node>");
        }
    }
    
    private void setMatch(final String match) {
        int index = 0;
        matchList = new ArrayList<String>(INT_16);

        firstMatchTopic = (match.indexOf(SLASH) != -1) ? match.substring(0, match.indexOf('/')) : match;

        while (index != -1) {
            final int end = match.indexOf(SLASH, index);
            if (end == -1) {
                matchList.add(match.substring(index));
                lastMatchTopic = match.substring(index);
                index = end;
            } else {
                matchList.add(match.substring(index, end));
                index = end + 1;
            }
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        try {
            output.write(StringUtils.getEntity(name));
        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        insideCDATA = true;
        try{
            output.write(CDATA_HEAD);
        }catch(final Exception e){
            logger.logException(e);
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName,
            final Attributes atts) throws SAXException {
        final String classAttrValue = atts.getValue(ATTRIBUTE_NAME_CLASS);

        try {
            if (classAttrValue != null &&
                    TOPIC_TOPIC.matches(classAttrValue) &&
                    !topicSpecList.contains(qName)){
                //add topic qName to topicSpecList
                topicSpecList.add(qName);
            }

            if ( startMap && !startDOM && classAttrValue != null && !hasWritten
                    &&(
                            MAP_TOPICMETA.matches(classAttrValue)
                            )){
                startDOM = true;
                output = strOutput;
                output.write("<map>");
            }

            if ( startMap && classAttrValue != null && !hasWritten &&(
                    MAP_NAVREF.matches(classAttrValue) ||
                    MAP_ANCHOR.matches(classAttrValue) ||
                    MAP_TOPICREF.matches(classAttrValue) ||
                    MAP_RELTABLE.matches(classAttrValue)
                    )){
                if (startDOM){
                    startDOM = false;
                    output.write("</map>");
                    output = ditaFileOutput;
                    processDOM();
                }else{
                    processDOM();
                }

            }

            if ( !startMap && !ELEMENT_NAME_DITA.equalsIgnoreCase(qName)){
                if (atts.getValue(ATTRIBUTE_NAME_ID) != null){
                    topicIdList.add(atts.getValue(ATTRIBUTE_NAME_ID));
                }else{
                    topicIdList.add("null");
                }
                if (matchList == null){
                    startMap = true;
                }else if ( topicIdList.size() >= matchList.size()){
                    //To access topic by id globally
                    startMap = checkMatch();
                }
            }



            outputElement(qName, atts);

        } catch (final Exception e) {
            logger.logException(e);
        }
    }


    private void outputElement(final String qName, final Attributes atts) throws IOException {
        final int attsLen = atts.getLength();
        output.write(LESS_THAN + qName);
        for (int i = 0; i < attsLen; i++) {
            final String attQName = atts.getQName(i);
            String attValue;
            attValue = atts.getValue(i);

            // replace '&' with '&amp;'
            //if (attValue.indexOf('&') > 0) {
            //	attValue = StringUtils.replaceAll(attValue, "&", "&amp;");
            //}
            attValue = StringUtils.escapeXML(attValue);

            output.write(new StringBuffer().append(STRING_BLANK)
                    .append(attQName).append(EQUAL).append(QUOTATION)
                    .append(attValue).append(QUOTATION).toString());
        }
        output.write(GREATER_THAN);
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        try {
            needResolveEntity = StringUtils.checkEntity(name);
            if(!needResolveEntity){
                output.write(StringUtils.getEntity(name));
            }
        } catch (final Exception e) {
            logger.logException(e);
        }
    }

    @Override
    public void write(final String outputFilename) {
        String filename = outputFilename;
        String file = null;
        String topic = null;
        File inputFile = null;
        File outputFile = null;

        try {
            if(filename.endsWith(SHARP)){
                // prevent the empty topic id causing error
                filename = filename.substring(0, filename.length()-1);
            }

            if(filename.lastIndexOf(SHARP)!=-1){
                file = filename.substring(0,filename.lastIndexOf(SHARP));
                topic = filename.substring(filename.lastIndexOf(SHARP)+1);
                setMatch(topic);
                startMap = false;
            }else{
                file = filename;
                matchList = null;
                startMap = false;
            }
            needResolveEntity = true;
            hasWritten = false;
            startDOM = false;
            inputFile = new File(file);
            outputFile = new File(file + FILE_EXTENSION_TEMP);
            ditaFileOutput = new OutputStreamWriter(new FileOutputStream(outputFile), UTF8);
            strOutput = new StringWriter();
            output = ditaFileOutput;

            topicIdList.clear();
            reader.parse(inputFile.toURI().toString());
        } catch (final Exception e) {
            logger.logException(e);
        }finally {
            try{
                ditaFileOutput.close();
            } catch (final Exception e) {
                logger.logException(e);
            }
        }
        try {
            if(!inputFile.delete()){
                final Properties prop = new Properties();
                prop.put("%1", inputFile.getPath());
                prop.put("%2", outputFile.getPath());
                logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
            if(!outputFile.renameTo(inputFile)){
                final Properties prop = new Properties();
                prop.put("%1", inputFile.getPath());
                prop.put("%2", outputFile.getPath());
                logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
        } catch (final Exception e) {
            logger.logException(e);
        }
    }
}