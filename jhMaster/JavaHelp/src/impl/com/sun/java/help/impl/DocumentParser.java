/*
 * @(#)DocumentParser.java	1.19 06/10/30
 * 
 * Copyright (c) 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @(#) DocumentParser.java 1.19 - last change made 10/30/06
 */
package com.sun.java.help.impl;

/**
 * This class is designed to be extended for use.  You use it in 
 * this way:
 *
 *	1. instantiate the object, passing an inputStream to the
 *	   constructor (a string can be passed as well).
 *
 *	2. call parse() to start it running.  Parse will return
 *	   when the eof of file is reached.
 *
 *	3. during the parse, each of the following calls will
 *	   be made.  The extensions should handle the calls as
 *	   necessary:
 *
 *		flush		Called for each contiguous block
 *				of text.
 *
 *		tag		Called for each complete tag
 *
 *                         pi	              Called for each PI (non-xml) recognized
 *
 *		doctype		Called for a doctype
 *
 *		comment		Called for each complete comment
 *
 *		errorString	Called for each parse error
 *
 *	4. As an extension &xxx; macros can be implemented.  For
 *	   each unrecognized &xxx; macro, a call to
 *	   documentAttribute is made that will return a string if
 *	   the macro name has a value.
 */

import java.io.*;
import java.util.Hashtable;

public abstract class DocumentParser {
    static final char EOF = (char)-1;

    protected Reader source;
    int		readOffset;
    ScanBuffer	buffer;
    ScanBuffer	escapeBuffer;
    ScanBuffer  documentSource;
    boolean     shouldCacheSource;

    /************************************************************************
    *****								*****
    *****		Public interface 				*****
    *****								*****
    ************************************************************************/

    public DocumentParser(InputStream in) {
	source = new MyBufferedReader(new InputStreamReader(in));
	init();
    }

    public DocumentParser(Reader src) {

	//..................................................
	// Check if the Reader src is buffered, and make it buffered
	// if it's not already
	//.................................................
	if (src instanceof MyBufferedReader) {
	    source = src;
	} else {
	    source = new MyBufferedReader(src);
	}
	init();
    }

    public void setInput(Reader src){
	//source = s;
	//.................................................. 
        // Check if the Reader src is buffered, and make it buffered 
        // if it's not already  
        //................................................. 
        if (src instanceof MyBufferedReader) { 
	    source = src;
        } else { 
	    source = new MyBufferedReader(src);
        }
    }

    //.....YK..... checking for null InputStream

    public void setInput(InputStream i, String encoding) throws UnsupportedEncodingException
    {
	if(i == null) {
	    source = null;
	    return;
	}
	source = new MyBufferedReader( new InputStreamReader(i, encoding));
    }

    /*
     * sets whether to cache document source
     */
    public void setShouldCacheSource(boolean state) {
        shouldCacheSource = state;
    }

    /*
     * return String containing the document source (monsanto)
     * Bug 4058915: File->HTML Source ... menu item shows edited source.
     */
    public String getDocumentSource() {
        if (!shouldCacheSource) {
            return null;
        } else {
	    int offset = 
		(0 == documentSource.length()) ? 0 : documentSource.length() - 1;
            return new String(documentSource.buf, 0, offset) +"\n";
        }
    }

    // ................................. YK (Inline)
    public void parse() throws IOException {
        char c = EOF;		// YK: checking for null
 
        buffer.clear();
        if(source != null) {	// YK: checking for null
	    c = readChar();
	}			// YK: checking for null
        for (;;){
	    if (c == EOF) {
                break;
            }
	    if (c == DocPConst.AMPERSAND) {
                c = parseEscape();
            } else if (c == DocPConst.LANGLE){
		buffer.flush(this);
		c = parseTag();
	    } else {
		// .................................................
		// Inlining the data access gives BIG performance win
		// .................................................
		// buffer.add(c); // YK: inline
		if (buffer.buflen >= buffer.buf.length){
		    char [] x = new char [buffer.buf.length * buffer.scale];
		    System.arraycopy(buffer.buf, 0, x, 0, buffer.buf.length);
		    buffer.buf = x;
		}

		// fixes bug 4056036 - cannot open bat-text files
		// (monsanto)
		if (c != DocPConst.RETURN) {
                    buffer.buf[buffer.buflen++] = c;
                }

		// ................................................. 
		// Inlining the data access gives BIG performance win 
		// ................................................. 

		//c = readChar(); // YK: inline
		if(myCount >= mySize) {
		    try {
			mySize = source.read(cb, 0, defaultCharBufferSize);
			if(mySize < 0) {
			    break;
			}  
			if(mySize == 0) {
			    System.err.println(" DocumentParser::parse() !!!ERROR !!! source.read(...) == 0");
			    break;
			}
			myCount = 0;
		    } catch (CharConversionException e) {
			throw e;
		    } catch (IOException e) {
			break;
		    }    
		}
		// ..........REMIND - removed .......// readOffset++;
		if (shouldCacheSource) {
                    documentSource.add(cb[myCount]);
                }
		c = cb[myCount++];
	    }
        }
        buffer.flush(this);
    }




    public void parseText() throws IOException {
	char c;

	tag("PRE", null, false, false);

	buffer.clear();
	c = readChar();
	for (;;) {
	    if (c == EOF) {
                break;
            }
	    buffer.add(c);
	    c = readChar();
	}
	buffer.flush(this);
    }

    /**
     * Invokes flush(). This method is provided so that flush() can 
     * be protected
     */
    protected void callFlush(char [] buf, int offset, int length)
    {
	flush(buf, offset, length);
    }

    /************************************************************************
    *****								*****
    *****		Methods to be overridden by subclasses		*****
    *****								*****
    ************************************************************************/

    /**
     * This method creates a block of text for a document.
     *
     * It should be overridden by the subclass
     */
    abstract protected void flush(char [] buf, int offset, int length);

    /**
     * This method inserts a comment
     *
     * It should be overridden by the subclass
     */
    abstract protected void comment(String s);

    /**
     * This method emits a tag
     *
     * It should be overridden by the subclass
     */
    abstract protected void tag(String name, TagProperties atts, boolean endTag, boolean emptyTag);

    /**
     * This method emits a pi
     *
     * It should be overridden by the subclass
     */
    abstract protected void pi(String target, String data);

    /**
     * This method emits a doctype.  Internal subset information is discarded
     *
     * It should be overridden by the subclass
     */
    abstract protected void doctype(String root, String publicId, String systemId);

    /**
     * This method looks up a &xxx; sequence in the document
     * properties (this is used for templates).  A return of null
     * means the proerty is undefined.
     *
     * It should be overridden by the subclass
     */
    abstract protected String documentAttribute(String name);

    /**
     * This method inserts a parse error string into the document
     *
     * It should be overridden by the subclass
     */
    abstract protected void errorString(String s);

    /************************************************************************
    *****								*****
    *****		Internal methods				*****
    *****								*****
    ************************************************************************/

    private void init() {
        // .........buffer initialization......................
        // bigger buffer size gives some performance win
	buffer = new ScanBuffer(8192, 4);	// YK 
        // ......... escapeBuffer initialization...............
        // bigger buffer size gives some performance win
	escapeBuffer = new ScanBuffer(8192,4);	// YK
	documentSource = new ScanBuffer(8192,4);	// YK
	readOffset = 0;
    }

    protected void findCloseAngleForComment(char c) throws IOException {
	buffer.add(c);
	for(;;){
	    c = readChar();
	    if (c == DocPConst.RANGLE) {
                break;
            }
	    buffer.add(c);
	}
	buffer.add(c);
	comment(buffer.extract(0));
	buffer.clear();
    }

    protected char handleCommentOrDoctype(char c) throws IOException {
	buffer.add(c);
	int offset = buffer.length();
	c = scanIdentifier(c);
	String name = buffer.extract(offset);

	if (! name.equals("DOCTYPE")) {
	    findCloseAngleForComment(c);
	    return readChar();
	}
	
	// a DOCTYPE
	c = skipWhite(c);

	// The root of the document...
	offset = buffer.length();
	c = scanIdentifier(c);
	String root = buffer.extract(offset);

	c = skipWhite(c);
	
	// If no externaID...
	if (c == DocPConst.RANGLE) {

	    buffer.clear();
	    return readChar();
	}

	offset = buffer.length();
	c = scanIdentifier(c);
	name = buffer.extract(offset);

	// two forms are valid:
	// "SYSTEM sysid" and "PUBLIC pubid sysid"

	String publicId = null;
	String systemId = null;

        switch (name) {
            case "SYSTEM":
                c = skipWhite(c);
                
                offset = buffer.length();
                c = scanQuotedString(c);
                systemId = buffer.extract(offset);
                
                doctype(root, null, systemId);
                
                if (c != DocPConst.RANGLE) {
                    findCloseAngleForComment(c);
                }
                buffer.clear();
                return readChar();
            case "PUBLIC":
                c = skipWhite(c);
                
                offset = buffer.length();
                c = scanQuotedString(c);
                publicId = buffer.extract(offset);
                
                c = skipWhite(c);
                
                offset = buffer.length();
                c = scanQuotedString(c);
                systemId = buffer.extract(offset);
                
                doctype(root, publicId, systemId);
                
                if (c != DocPConst.RANGLE) {
                    findCloseAngleForComment(c);
                }
                buffer.clear();
                return readChar();
            default:
                // we ignore this; altough it is likely an internal DTD subset
                if (c != DocPConst.RANGLE) {
                    findCloseAngleForComment(c);
                }
                findCloseAngleForComment(c);
                
                doctype(root, null, null);
                
                buffer.clear();
                return readChar();
        }
    }


    protected void setXmlEntities(TagProperties attr) {
	// Don't do anything for now
    }

    protected char parseTag() throws IOException {
	char		c;
	String		name;
	TagProperties	attributes;
	int		offset;
	int		tagStartOffset;
	boolean		endTag;
	boolean		emptyTag;

	/**
	 * Comments are handled in the following way:
	 * 1) If the first character is "!" AND  NOT followed by "--" 
	 *    then everything is a comment till the subsequent closing 
	 *    angle bracket.
	 * 2) If the first character is "!" AND is followed by "--" then
	 *    everything till "--" followed by a closing angle bracket
	 *    is marked as a comment. This can include other closing and
	 *    opening angle brackets.
	 *
	 * N.B Netscape follows the above two rules with a caveat to rule
	 *     2 from above. If it does not find "--" followed by a closing
	 *     angle bracket in the rest of the document, it behaves as rule
	 *     1 i.e the subsequent closing angle bracket is taken as the
	 *     end of the comment.
	 *
	 * Xml entities are handled in the following way:
	 * 1) If the first character is "?" AND is followed by "xml" 
	 *    then everything is a xml entity till the subsequent closing 
	 *    "?" followed by an angle bracket.
	 *
	 */

	buffer.clear();
	tagStartOffset = 0;
	c = DocPConst.LANGLE;
	buffer.add(DocPConst.LANGLE);
	c = readChar();
	if (c == DocPConst.EXCLAIM){		//  comment follows
	    int resetPos = 0;
	    buffer.add(DocPConst.EXCLAIM);
	    // =-= jd BEGIN HACK
	    // Taking the check for - out since Netscape just uses <!
	    // to mark a comment
	    c = readChar();
	    if (c != DocPConst.MINUS){
		// a shorthand comment or a DOCTYPE
		return handleCommentOrDoctype(c);
	    }
	    buffer.add(c);      // =-= jd Added this line
	    c = readChar();
	    if (c != DocPConst.MINUS){	// it's still a "shorthand" comment
		findCloseAngleForComment(c);
		return readChar();
	    }
	    buffer.add(c);     // =-= jd Added this line
	    // just in case of the NB comment from above, set a fallback pos
	    resetPos = buffer.length();
	    // source.reset() doesn't work at all :(
	    // without that, we can't do NB on comments. - the
	    // comment goes to EOF, unless it encounters "-->"
	    //if (source.markSupported()) {
	    //  resetPos = buffer.length();
	    //  try {
	    //	source.mark(64);	// pass in readAheadLimit
	    //  } catch (IOException e){
	    //  }
	    //}

	    // handled "shorthand" comments - now need to look for "-->"
	    int nDash = 0;
	    for(;;) {
		c = readChar();
		if (c == EOF) {
		    commentEOFError(resetPos);
		    break;
		}
		if (c != DocPConst.MINUS) {
		    buffer.add(c);
		    continue;
		}
		while (c == DocPConst.MINUS) {
		    buffer.add(c);
		    nDash ++;
		    c = readChar();
		}
		if (c == EOF) {
		    commentEOFError(resetPos);
		    break;
		}
		buffer.add(c);
		if ((nDash >= 2) && (c == DocPConst.RANGLE)) {
		    comment(buffer.extract(0));
		    buffer.clear();
		    return readChar();
		}
		nDash = 0;
	    }
	}
	if (c == DocPConst.QUESTION){		//  xml & PI declarations follows
	    int resetPos = 0;
	    StringBuffer target = new StringBuffer();
	    buffer.add(DocPConst.QUESTION);
	    while (((c = readChar()) != DocPConst.DQUOTE) &&
		   c != DocPConst.SPACE  &&
		   c != DocPConst.TAB    &&
		   c != DocPConst.NEWLINE &&
		   c != DocPConst.RANGLE) {
		buffer.add(c);
		target.append(c);
	    }

	    if (! target.toString().equals("xml")) {
		// we have a PI ...
		buffer.clear();	// reset the buffer
		while (true) {
		    while ((c = readChar()) != DocPConst.QUESTION &&
			   c != EOF) {
			buffer.add(c);
		    }
		    if (c == EOF) {
			eofError();
			return readChar();
		    }
		    c = readChar();
		    if (c != DocPConst.RANGLE &&
			c != EOF) {
			buffer.add(DocPConst.QUESTION);
			buffer.add(c);
		    }
		    if (c == EOF) {
			eofError();
			return readChar();
		    } else {
			pi(target.toString(), buffer.extract(0));
			return readChar();
		    }
		}
	    }
	    // discard that target
	    target = null;

	    // OK so far 
	    //
	    // try to find the attributes
	    c = readChar();
	    attributes = null;
	    for (;;){
		c = skipWhite(c);
		if (c == EOF){
		    eofError();
		    return c;
		}
		if (c == DocPConst.QUESTION) {
                    break;
                }

		offset = buffer.length();
		c = scanIdentifier(c);
		if (offset == buffer.length()){
		    error("Expecting an attribute");
		    skipToCloseAngle(c);
		    return readChar();
		}
		String attname = buffer.extract(offset);
		c = skipWhite(c);
		if (attributes == null) {
                    attributes = new TagProperties();
                }
		String	attvalue;
		if (c == DocPConst.EQUALS) {		// parsing attribute value
		    buffer.add(c);
		    c = readChar();
		    int	voff;
		    // ignore whitespace between the EQUALS and the attr value
		    // because netscape does	(bug#4045870)
		    c = skipWhite(c);
		    // delimit on RANGLE and LANGLE in case of incomplete line
		    if (c == DocPConst.QUESTION || c == DocPConst.LANGLE ){
			attvalue = "";
		    } else if (c == DocPConst.DQUOTE){
			buffer.add(c);
			voff = buffer.length();
			for (;;){
			    c = readChar();
			    if (c == EOF){
				eofError();
				return c;
			    }
			    if (c == DocPConst.DQUOTE) {
                                break;
                            }
			    // transform &
			    if (c == DocPConst.AMPERSAND) {
                                c = parseEscape();
                            }
			    buffer.add(c);
			}
			attvalue = buffer.extract(voff);
			buffer.add(c);
			c = readChar();
		    } else {
			voff = buffer.length();
			buffer.add(c);
			for (;;){
			    c = readChar();
			    if (c == EOF){
				eofError();
				return c;
			    }
			    if (c == DocPConst.DQUOTE ||
				c == DocPConst.SPACE  ||
				c == DocPConst.TAB    ||
				c == DocPConst.NEWLINE ||
				c == DocPConst.QUESTION) {
                                break;
                            }
			    // transform &
			    if (c == DocPConst.AMPERSAND) {
                                c = parseEscape();
                            }
			    buffer.add(c);
			}
			attvalue = buffer.extract(voff);
		    }
		} else {
		    attvalue = "true";
		}
		attributes.put(attname, attvalue);
	    }
	    c = readChar();
	    if (c == EOF) {
		eofError();
		return readChar();
	    }
	    c = skipWhite(c);
	    buffer.add(c);
	    if ((c == DocPConst.RANGLE)) {
		setXmlEntities(attributes);
		buffer.clear();
		return readChar();
	    } else {
		error ("Expecting ?>");
		skipToCloseAngle(c);
		return readChar();
	    }
	}
	c = skipWhite(c);
	if (c == EOF){
	    eofError();
	    return c;
	}
	if (c == DocPConst.SLASH){
	    buffer.add(c);
	    c = skipWhite(readChar());
	    endTag = true;
	    emptyTag = false;
	} else {
	    endTag = false;
	    emptyTag = false;
	}
	offset = buffer.length();
	c = scanIdentifier(c);
	name = buffer.extract(offset);
	attributes = null;
	for (;;){
	    c = skipWhite(c);
	    if (c == EOF){
		eofError();
		return c;
	    }

	    if (c == DocPConst.RANGLE) {
                break;
            }

	    if (c == DocPConst.SLASH) {
		buffer.add(c);
		c = readChar();
		if (c == DocPConst.RANGLE) {
		    endTag = true;
		    emptyTag = true;
		    break;
		} else {
		    error("Expecting />");
		    skipToCloseAngle(c);
		    return readChar();
		}
	    }

	    if (c == DocPConst.LANGLE)  {

		// this could be a comment inside a tag e.g. 
		// <TABLE width=500 <!-- border=5 --> color="e0e0e0"
		// right now we will ignore the comment and
		// close the tag as Netscape does.

		// OR

		// our learned HTML author has forgotten to
		// close the tag. e.g.
		// <TABLE width=500 border=5 color="e0e0e0"
		// <CAPTION>....

		tag(name, attributes, endTag, false);
		buffer.clear();
		return (DocPConst.LANGLE);
	    }

	    offset = buffer.length();
	    c = scanIdentifier(c);
	    if (offset == buffer.length()){
		error("Expecting an attribute (2)");
		skipToCloseAngle(c);
		return readChar();
	    }
	    String attname = buffer.extract(offset);
	    c = skipWhite(c);
	    if (attributes == null) {
                attributes = new TagProperties();
            }
	    String	attvalue;
	    if (c == DocPConst.EQUALS) {		// parsing attribute value
		buffer.add(c);
		c = readChar();
		int	voff;
		// ignore whitespace between the EQUALS and the attr value
		// because netscape does	(bug#4045870)
		c = skipWhite(c);
		// delimit on RANGLE and LANGLE in case of incomplete line
		if (c == DocPConst.RANGLE || c == DocPConst.LANGLE ){
		    attvalue = "";
		} else if (c == DocPConst.DQUOTE){
		    buffer.add(c);
		    voff = buffer.length();
		    for (;;){
			c = readChar();
			if (c == EOF){
			    eofError();
			    return c;
			}
			if (c == DocPConst.DQUOTE) {
                            break;
                        }
			// transform &
			if (c == DocPConst.AMPERSAND) {
                            c = parseEscape();
                        }
			buffer.add(c);
		    }
		    attvalue = buffer.extract(voff);
		    buffer.add(c);
		    c = readChar();
		} else {
		    voff = buffer.length();
		    buffer.add(c);
		    for (;;){
			c = readChar();
			if (c == EOF){
			    eofError();
			    return c;
			}
			if (c == DocPConst.DQUOTE ||
			    c == DocPConst.SPACE  ||
			    c == DocPConst.TAB    ||
			    c == DocPConst.NEWLINE ||
			    c == DocPConst.RANGLE) {
                            break;
                        }
			// transform &
			if (c == DocPConst.AMPERSAND) {
                            c = parseEscape();
                        }
			buffer.add(c);
		    }
		    attvalue = buffer.extract(voff);
		}
	    } else {
		attvalue = "true";
	    }
	    attributes.put(attname, attvalue);
	}
	tag(name, attributes, endTag, emptyTag);
	buffer.clear();
	return readChar();
    }


    protected char parseEscape() throws IOException {
	char	c;
	int	offset;

	offset = buffer.length();
	buffer.add(DocPConst.AMPERSAND);
	c = readChar();
	if (c == EOF){
	    generateError(offset);
	    return c;
	}
	if (c == DocPConst.HASH){
	    int	x;

	    x = 0;
	    for (;;){
		c = readChar();
		if (c == EOF){
		    generateError(offset);
		    return c;
		} else if (c == DocPConst.SEMICOLON) {
		    c = DocPConst.NULL;
		    break;
		} else if (!Character.isDigit(c)){
		    if (x > 0) {
			break;
		    } else {
			error("Expecting a digit");
			generateError(offset);
			return c;
		    }
		}
		buffer.add(c);
		x = x * 10 + Character.digit(c, 10);
	    }
	    buffer.reset(offset);
	    buffer.add((char)x);
	} else if (Character.isLowerCase(c) ||
		   Character.isUpperCase(c)){
	    if (entities == null) {
                initEntities();
            }

	    escapeBuffer.clear();
	    escapeBuffer.add(c);
	    for (;;){
		buffer.add(c);
		c= readChar();
		if (c == EOF){
		    generateError(offset);
		    return c;
		}
		if (Character.isLowerCase(c) ||
		    Character.isUpperCase(c)){
		    escapeBuffer.add(c);
		    if (entities.get(escapeBuffer.extract(0)) != null) {
			c = readChar();
			if (c == DocPConst.SEMICOLON) {
                            c = DocPConst.NULL;
                        }
			break;
		    }
		} else if (c == DocPConst.SEMICOLON) {
		    c = DocPConst.NULL;
		    break;
		} else {
		    error("Expecting a letter");
		    generateError(offset);
		    return c;
		}
	    }
	    String s = escapeBuffer.extract(0);

	    buffer.reset(offset);

	    Character x = entities.get(s);

	    if (x != null){
		return x.charValue();
	    } else {
		String a = documentAttribute(s);

		if (a != null){
		    int	i;

		    for (i = 0; i < a.length(); i++) {
                        buffer.add(a.charAt(i));
                    }
		}
	    }
	} else {
	    error("Expecting a letter o");
	    generateError(offset);
	    return c;
	}
	if (c != DocPConst.NULL) {
            return c;
        } else {
            return readChar();
        }
    }

    Hashtable<String, Character> entities;

    protected void initEntities() {
	entities = new Hashtable<>();

	entities.put("quot",	Character.valueOf(DocPConst.DQUOTE));
	entities.put("amp",	Character.valueOf(DocPConst.AMPERSAND));
	entities.put("gt",	Character.valueOf(DocPConst.RANGLE));
	entities.put("lt",	Character.valueOf(DocPConst.LANGLE));

	entities.put("nbsp",	Character.valueOf((char)160));

	entities.put("copy",	Character.valueOf((char)169));

	entities.put("Agrave",	Character.valueOf((char)192));
	entities.put("Aacute",	Character.valueOf((char)193));
	entities.put("Acirc",	Character.valueOf((char)194));
	entities.put("Atilde",	Character.valueOf((char)195));
	entities.put("Auml",	Character.valueOf((char)196));
	entities.put("Aring",	Character.valueOf((char)197));
	entities.put("AElig",	Character.valueOf((char)198));
	entities.put("Ccedil",	Character.valueOf((char)199));
	entities.put("Egrave",	Character.valueOf((char)200));
	entities.put("Eacute",	Character.valueOf((char)201));
	entities.put("Ecirc",	Character.valueOf((char)202));
	entities.put("Euml",	Character.valueOf((char)203));
	entities.put("Igrave",	Character.valueOf((char)204));
	entities.put("Iacute",	Character.valueOf((char)205));
	entities.put("Icirc",	Character.valueOf((char)206));
	entities.put("Iuml",	Character.valueOf((char)207));

	entities.put("Ntilde",	Character.valueOf((char)209));
	entities.put("Ograve",	Character.valueOf((char)210));
	entities.put("Oacute",	Character.valueOf((char)211));
	entities.put("Ocirc",	Character.valueOf((char)212));
	entities.put("Otilde",	Character.valueOf((char)213));
	entities.put("Ouml",	Character.valueOf((char)214));

	entities.put("Oslash",	Character.valueOf((char)216));
	entities.put("Ugrave",	Character.valueOf((char)217));
	entities.put("Uacute",	Character.valueOf((char)218));
	entities.put("Ucirc",	Character.valueOf((char)219));
	entities.put("Uuml",	Character.valueOf((char)220));
	entities.put("Yacute",	Character.valueOf((char)221));
	entities.put("THORN",	Character.valueOf((char)222));
	entities.put("szlig",	Character.valueOf((char)223));
	entities.put("agrave",	Character.valueOf((char)224));
	entities.put("aacute",	Character.valueOf((char)225));
	entities.put("acirc",	Character.valueOf((char)226));
	entities.put("atilde",	Character.valueOf((char)227));
	entities.put("auml",	Character.valueOf((char)228));
	entities.put("aring",	Character.valueOf((char)229));
	entities.put("aelig",	Character.valueOf((char)230));
	entities.put("ccedil",	Character.valueOf((char)231));
	entities.put("egrave",	Character.valueOf((char)232));
	entities.put("eacute",	Character.valueOf((char)233));
	entities.put("ecirc",	Character.valueOf((char)234));
	entities.put("euml",	Character.valueOf((char)235));
	entities.put("igrave",	Character.valueOf((char)236));
	entities.put("iacute",	Character.valueOf((char)237));
	entities.put("icirc",	Character.valueOf((char)238));
	entities.put("iuml",	Character.valueOf((char)239));
	entities.put("eth",	Character.valueOf((char)240));
	entities.put("ntilde",	Character.valueOf((char)241));
	entities.put("ograve",	Character.valueOf((char)242));
	entities.put("oacute",	Character.valueOf((char)243));
	entities.put("ocirc",	Character.valueOf((char)244));
	entities.put("otilde",	Character.valueOf((char)245));
	entities.put("ouml",	Character.valueOf((char)246));

	entities.put("oslash",	Character.valueOf((char)248));
	entities.put("ugrave",	Character.valueOf((char)249));
	entities.put("uacute",	Character.valueOf((char)250));
	entities.put("ucirc",	Character.valueOf((char)251));
	entities.put("uuml",	Character.valueOf((char)252));
	entities.put("yacute",	Character.valueOf((char)253));
	entities.put("thorn",	Character.valueOf((char)254));
	entities.put("yuml",	Character.valueOf((char)255));
    }

    // ....................................................
    // YK: optimization: Inlining
    // ....................................................

    protected char scanIdentifier(char c) throws IOException {
        for (;;) {
	    if (! (
		   c == DocPConst.HORIZBAR  ||
		   c == DocPConst.COLON ||
		   (c >= DocPConst.ZERO && c <= DocPConst.NINE)		||
		   Character.isLetter(c)
				// Used to be Character.isUpperCase(c)
		      )
		   ) {
		break;
	    }
	    // ............................................
	    // Inlining again an again
	    // ............................................
	    // buffer.add(c); // YK: inline
	    if (buffer.buflen >= buffer.buf.length){
		char [] x = new char [buffer.buf.length * buffer.scale];
		System.arraycopy(buffer.buf, 0, x, 0, buffer.buf.length);
		buffer.buf = x;
	    }

	    // ............................................
	    // Inlining again an again 
	    // ............................................
	    buffer.buf[buffer.buflen++] = c;
 
	    //c = readChar(); // YK: inline
	    if(myCount >= mySize) {
		try {
		    mySize = source.read(cb, 0, defaultCharBufferSize);
		    if(mySize < 0) {
			break;
		    }
		    if(mySize == 0) {
			System.err.println(" DocumentParser::scanIdentifier() !!!ERROR !!! source.read(...) == 0");
			break;
		    }
		    myCount = 0;
		} catch (CharConversionException e) {
		    throw e;
		} catch (IOException e) {
		    break;
		}
	    }
	    //...........REMIND............removed......// readOffset++;
	    if (shouldCacheSource) {
                documentSource.add(cb[myCount]);
            }
	    c = cb[myCount++];
        }
        return c;
    }


    // ......................................................
    // Scan a single or double quoted string
    // ......................................................
    protected char scanQuotedString(char c) throws IOException {
	c = skipWhite(c);
	if (c == DocPConst.DQUOTE) {
	    for (;;) {
		c = readChar();
		if (c == DocPConst.DQUOTE ||
		    c == DocPConst.RANGLE) {
		    break;
		}
		buffer.add(c);
	    }
	    return readChar();
	} else if (c == DocPConst.QUOTE) {
	    for (;;) {
		c = readChar();
		if (c == DocPConst.QUOTE ||
		    c == DocPConst.RANGLE) {
		    break;
		}
		buffer.add(c);
	    }
	    return readChar();
	} else {
	    return c;
	}
    }
    
    // ......................................................
    //  This whole method is inlined including the data reading
    // ................. YK: Inline
    protected char skipWhite(char c) throws IOException {
	while (c == DocPConst.SPACE  ||
	       c == DocPConst.RETURN ||
	       c == DocPConst.TAB    ||
	       c == DocPConst.NEWLINE)   {
	    // ............................................
	    // Inlining again an again 
	    // ............................................
	    // buffer.add(c); // YK: inline
	    if (buffer.buflen >= buffer.buf.length){
		char [] x = new char [buffer.buf.length * buffer.scale];
		System.arraycopy(buffer.buf, 0, x, 0, buffer.buf.length);
		buffer.buf = x;
	    }
	    buffer.buf[buffer.buflen++] = c;
 
	    //c = readChar(); // YK: inline
	    // ............................................ 
	    // Inlining again an again  
	    // ............................................ 

	    if(myCount >= mySize) {
		try {
		    mySize = source.read(cb, 0, defaultCharBufferSize);
		    if(mySize < 0) {
			break;
		    }
		    if(mySize == 0) {
			System.err.println(" DocumentParser::parse() !!!ERROR !!! source.read(...) == 0");
			break;
		    }
		    myCount = 0;
		} catch (CharConversionException e) {
		    throw e;
		} catch (IOException e) {
		    break;
		}
	    }
	    //...........REMIND............removed......// readOffset++;
	    if (shouldCacheSource) {
                documentSource.add(cb[myCount]);
            }
	    c = cb[myCount++];
	}
	return c;
    }


    // ................................................................
    // The readChar method is heavily inlined
    // (all the calls to sychronized read() are removed)
    // The read() also creates on char array every time !!! - VERY SLOW
    // YK 
    // .................................................................
    int defaultCharBufferSize = 8192; 
    char cb[] = new char[defaultCharBufferSize]; 
    int mySize =  0;
    int myCount = 0;
    protected char readChar() throws IOException {
	if(myCount  >=mySize) {
	    try {
                mySize = source.read(cb, 0, defaultCharBufferSize);
		if(mySize < 0) {
		    return EOF;
		}
		if(mySize == 0) {
		    System.err.println(" DocumentParser::readChar() !!!ERROR !!! source.read(...) == 0");
		    return EOF;
		}
		myCount = 0;
	    } catch (CharConversionException e) {
		throw e;
	    } catch (IOException e) {
		return EOF;
	    }
	}
	//...........REMIND............removed......// readOffset++;
	if (shouldCacheSource) {
            documentSource.add(cb[myCount]);
        }
	return cb[myCount++];
    }  

    /**
     * This method is called when parsing a tag and an error is
     * encountered.  It skips to the closing angle bracket, 
     * treating the entire tag as error text.  The generateError
     * call at the end stuffs the full tag into the document
     * as an error.
     */

    // YK: Inlining
    // ................................................................ 
    // The skipToCloseAngle method is heavily inlined
    // (all the calls to sychronized read() are removed) 
    // The read() also creates on char array every time !!! - VERY SLOW   
    // YK 
    // ................................................................. 

    protected void skipToCloseAngle(char c) throws IOException {
        for (;;) { 
	    //buffer.add(c);  


            if (buffer.buflen >= buffer.buf.length){
                char [] x = new char [buffer.buf.length * buffer.scale];
                System.arraycopy(buffer.buf, 0, x, 0, buffer.buf.length);
                buffer.buf = x;
	    }
	    buffer.buf[buffer.buflen++] = c;

	    if (c == DocPConst.RANGLE)  {
		break;
	    }



	    //c = readChar();


	    if(myCount >= mySize) {
		try {
		    mySize = source.read(cb, 0, defaultCharBufferSize);
		    if(mySize < 0) {
                        break;
		    }
		    if(mySize == 0) {
                        System.err.println(" DocumentParser::skipToCloseAngle() !!!ERROR !!! source.read(...) == 0");
                        break;
		    }
		    myCount = 0;
		} catch (CharConversionException e) {
		    throw e;
		} catch (IOException e) {
		    break;
		}
	    }
	    //...........REMIND............removed......// readOffset++;
	    c = cb[myCount++];
        }


        generateError(0);
    }

    protected void generateError(int offset) {
	String s = buffer.extract(offset);

	buffer.reset(offset);
	buffer.flush(this);
	errorString(s);
    }

    protected void commentEOFError(int resetTo) {
	/*  tried to use source.reset, but didn't work this way...
	    // for some reason, this isn't resetting the source (BufferedReader)
	    source.reset();
	    buffer.reset(resetTo);
	    char c = readChar();
	    if (c != DocPConst.RANGLE) 
	    findCloseAngleForComment(c);
	    else {
	    buffer.add(c);
	    comment(buffer.extract(0));
	    buffer.clear();
	    }
	    } catch (IOException e) {
	    }
	    */
	//System.err.println("DocumentParser:: comment went past end of buffer!");
	eofError();
    }

    protected void eofError() {
	error("Unexpected end of file");
	generateError(0);
    }

    void error(String s) {
	System.err.println("DocumentParser Error: "+s);
    }
} // class DocumentParser

class ScanBuffer {
    char []	buf;
    int		buflen;
    int scale = 2;

    ScanBuffer() {
	buf = new char [ 256 ];
    }

    //...........................................................
    // YK -extra constructor with the initial buffer size
    //...........................................................
    ScanBuffer(int l, int s) {
        buf = new char [l];
	scale = s;
    }

    protected void clear() {
	buflen = 0;
    }
 
    protected void reset(int offset) {
	buflen = offset;
    }

    protected void flush(DocumentParser owner) {
	if (buflen > 0){
	    owner.callFlush(buf, 0, buflen);
	    buflen = 0;
	}
    }

    protected void add(char c) {
	if (buflen >= buf.length){
	    char [] x = new char [buf.length * scale];
	    System.arraycopy(buf, 0, x, 0, buf.length);
	    buf = x;
	}
	buf[buflen++] = c;
    }

    protected int length() {
	return buflen;
    }

    @Override
    public String toString() {
 	return "ScanBuffer, buf = " + buf + ", buflen = " + buflen;
    } 

    //....................................................
    // YK: optimized creating new strings
    // without any data copying (big performance win)
    // as this method is called allover the place
    //....................................................
   
    protected String extract(int offset) {
	return new String(buf, offset, buflen - offset);
    }

} // class ScanBuffer


// YK: This class is created to fix NullPointerException
// in BufferedReader::read(...) having lock == null

class MyBufferedReader extends BufferedReader {
    public MyBufferedReader(Reader in, int sz) { super(in, sz); }  
    public MyBufferedReader(Reader in) { super(in); }  
    @Override
    public int read(char cbuf[], int off, int len) throws IOException {
	if(lock == null) {
	    //System.err.println("read(): -------------------lock == null");
	    return -1;
	}
	//System.err.println("read(): +++++++++++++++++++lock = " + lock);
	return super.read(cbuf, off, len);
    }
}
