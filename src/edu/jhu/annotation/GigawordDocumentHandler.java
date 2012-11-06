//
// These are the tools used to annotate Annotated Gigaword (Napoles, 
// Gormley, and Van Durme, 2012) using a modified Stanford CoreNLP 
// pipeline. The current version here is StanfordCoreNLP v.1.3.2. The 
// primary modification is to use parse trees as input (instead of 
// parsing sentences using the Stanford parser/grammar). The pipeline 
// is also modified to print the root dependency.
//
// GigawordDocumentHandler sequentially reads a Gigaword-style file 
// containing parsed sentences and converts sentences into documents
// (necessary for document-wide coreference resolution).
//
// Courtney Napoles, cdnapoles@gmail.com
// 2012-07-03

package edu.jhu.annotation;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;

public class GigawordDocumentHandler {
	String inputpath;
	BufferedReader input;
	ArrayList<String> buffer;
	int count = 0;
	boolean endOfFile = false;
	boolean useSGMLformat = true;
	boolean debug = false;
	ArrayList<String> docIds = null;
	int docIdIndex = 0;
	String docTypeName = "DOC", textTypeName = "TEXT", parsePrefix = "( (";
	String currentDocument = null;
    boolean justSents = false;
    int sentenceCount = 1; // for flat files, no document structure

	public GigawordDocumentHandler(String filepath) {
		inputpath = filepath;
		if (debug) {
			System.err.println("Loading documents from " + inputpath);
		}
	}

	/**
	 * these options are currently untested, but they work in theory
	 * 
	 * @param useSGML
	 * @param textTypeName
	 * @param docTypeName
	 * @param parsePrefix
	 */
	public void setOptions(boolean useSGML, String textTypeName,
			       String docTypeName, String parsePrefix, boolean debug, boolean justSents) {
		this.textTypeName = textTypeName;
		this.docTypeName = docTypeName;
		this.parsePrefix = parsePrefix;
		this.useSGMLformat = useSGML;
		this.justSents = justSents;
		this.debug = debug;
		if (debug) {
			if (useSGMLformat) {
				System.err.println("Using text type <" + textTypeName
					+ ">, doc type <" + docTypeName + ">, and parse prefix \""
					+ parsePrefix + "\".");
			} else
				System.err.println("Parse prefixis \"" + parsePrefix + "\".");
		}
	}

	/**
	 * can read gzip or uncompressed files
	 * 
	 * @throws IOException
	 */
	public void openReader() throws IOException {
		InputStreamReader isr = null;
		FileInputStream fis = new FileInputStream(inputpath);
		if (inputpath.endsWith(".gz")) {
			GZIPInputStream gzis = new GZIPInputStream(fis);
			isr = new InputStreamReader(gzis);
		} else {
			isr = new InputStreamReader(fis);
		}
		input = new BufferedReader(isr);
		String s = null;
		int i = 0;
		// maintain all header info before the first doc element (default is
		// "DOC")
		if (useSGMLformat) {
		while (!(s = input.readLine()).startsWith("<" + docTypeName)) {
			i++;
			System.out.println(s);
			input.mark(0);
		}
		input.reset();
		}
	}

	public boolean fileEmpty() {
		return endOfFile;
	}

	/**
	 * use this to only annotate specific DOC ids in the file, and path has a
	 * list of the DOC ids to annotate
	 * 
	 * @param path
	 * @throws IOException
	 */
	public void setDocList(String path) throws IOException {
		docIds = new ArrayList<String>();
		File f = new File(path);
		if (!f.exists()) {
			docIds.add(path);
			return;
		}
		FileInputStream fis = new FileInputStream(path);
		InputStreamReader isr = new InputStreamReader(fis);
		BufferedReader in = new BufferedReader(isr);
		String line;
		while ((line = in.readLine()) != null) {
			docIds.add(line.trim());
		}
		in.close();
		isr.close();
		fis.close();
	}

	/**
	 * prints out any lines left in the buffer or unread in the file before
	 * closing the bufferedreader
	 * 
	 * @throws IOException
	 */
	public void closeReader() throws IOException {
		String line;
		while ((line = input.readLine()) != null) {
			System.out.println(line);
		}
		endOfFile = true;
		input.close();
	}

	/**
	 * read the next document (with the type docEntity, default DOC) and convert
	 * it to a Stanford annotation
	 * 
	 * @return
	 * @throws IOException
	 */
	public Annotation getNextDocumentAnnotation() throws IOException {
		ArrayList<CoreMap> sentences = new ArrayList<CoreMap>();
		String line = "";
		boolean inText = false;
		int docSize = 0;

		// inDocToAnnotate is always true unless a list of docIds is provided.
		// See setDocList().
		boolean inDocToAnnotate = true;
		if (docIds != null)
			inDocToAnnotate = false;

		while (true) {
			line = input.readLine();
			if (line == null) {
				endOfFile = true;
				break;
			}
			line = line.trim();
			if (useSGMLformat) {
				if (!inText && line.startsWith("<" + docTypeName)) {
					if (!inDocToAnnotate
							&& line.contains(docIds.get(docIdIndex))) {
						inDocToAnnotate = true;
						docIdIndex++;
					}
					currentDocument = line;
					if (debug)
						System.err.println("Reading " + currentDocument);
				}
				if (!inDocToAnnotate)
					continue;
				if (!inText && line.startsWith("<" + textTypeName + ">")) {
					inText = true;
				}
				if (!inText && line.startsWith("</" + docTypeName)) {
					break;
				} else if (inText) {
					if (line.startsWith("</" + textTypeName)) {
						inText = false;
					} 
				}
				System.out.println(line);
			} else {
				inText = true;
			}

			if (inText && line.startsWith(parsePrefix)) {
				docSize++;
				CoreMap newSentence = getSentence(line);
				if (newSentence != null) {
					sentences.add(newSentence);
				}
				if (justSents && docSize % 100 == 0) {
				    if (debug) System.err.println("annotating "+docSize);
				    return sentencesToDocument(sentences);
				}

			}
		}
		if (!inDocToAnnotate)
			return null;
		return sentencesToDocument(sentences);
	}
	
	/**
	 * not used here, but escapes/converts tokens for XML
	 * 
	 * @param s
	 * @return
	 */
	public String escapeXmlCharacters(String s) {
		s = s.replace("&", "&amp;");
		s = s.replace("<", "&lt;");
		s = s.replace(">", "&gt;");
		char[] cc = s.toCharArray();
		for (char c : cc) {
			// substitute a dollar sign for all other currency signs
			if (c > 161 && c < 166) {
				s = s.replace("" + c, "$");
			}
		}
		return s;
	}

	/**
	 * convert a parsed String into a CoreMap annotation
	 * 
	 * @param parse
	 * @return
	 * @throws IOException
	 */
	public CoreMap getSentence(String parse) throws IOException {
		if (parse.trim().length() == 0) {
			if (debug) {
				System.err.println("Empty parse in " + currentDocument);
			}
			return null;
		}
		Tree tree = Tree.valueOf(parse);

		String text = getText(tree);
		if (text == null) {
			if (debug) {
				System.err.println(currentDocument + ": No leaves in tree: "
						+ parse);
			}

			return null;
		}
		Annotation sentence = new Annotation(text);
		sentence.set(TreeAnnotation.class, tree);
		List<Tree> leaves = tree.getLeaves();
		List<CoreLabel> tokens = new ArrayList<CoreLabel>(leaves.size());
		sentence.set(TokensAnnotation.class, tokens);
		for (int i = 0; i < leaves.size(); i++) {
			Tree leaf = leaves.get(i);
			CoreLabel token = (CoreLabel) leaf.label();
			tokens.add(token);
		}
		try {
			for (Tree leaf : leaves) {
				CoreLabel token = (CoreLabel) leaf.label();
				token.set(PartOfSpeechAnnotation.class, leaf.parent(tree)
						.value());
			}
		} catch (NullPointerException e) {
			if (debug) {
				System.err.println("Error in " + currentDocument);
				e.printStackTrace();
			}
			return null;
		}
		return sentence;
	}

	/**
	 * convert a list of sentences into a document Annotation
	 * 
	 * @param sentences
	 * @return
	 */
	public Annotation sentencesToDocument(List<CoreMap> sentences) {
		if (sentences.size() == 0) {
			if (debug) {
				System.err.println("0 sentences in document" + currentDocument);
			}
			return null;
		}
		String docText = null;
		Annotation document = new Annotation(docText);
		document.set(SentencesAnnotation.class, sentences);
		List<CoreLabel> docTokens = new ArrayList<CoreLabel>();
		int sentIndex = 1;
		if (justSents) {
		    sentIndex = sentenceCount;
		}
		int tokenBegin = 0;
		for (CoreMap sentAnno : sentences) {
			if (sentAnno == null) {
				continue;
			}
			List<CoreLabel> sentTokens = sentAnno.get(TokensAnnotation.class);
			docTokens.addAll(sentTokens);
			int tokenEnd = tokenBegin + sentTokens.size();
			sentAnno.set(TokenBeginAnnotation.class, tokenBegin);
			sentAnno.set(TokenEndAnnotation.class, tokenEnd);
			sentAnno.set(SentenceIndexAnnotation.class, sentIndex);
			sentIndex++;
			sentenceCount++;
			tokenBegin = tokenEnd;
		}
		document.set(TokensAnnotation.class, docTokens);
		int i = 0;
		for (CoreLabel token : docTokens) {
			String tokenText = token.get(TextAnnotation.class);
			token.set(CharacterOffsetBeginAnnotation.class, i);
			i += tokenText.length();
			token.set(CharacterOffsetEndAnnotation.class, i);
			i++; // Skip space
		}
		for (CoreMap sentenceAnnotation : sentences) {
			if (sentenceAnnotation == null) {
				continue;
			}
			List<CoreLabel> sentenceTokens = sentenceAnnotation
					.get(TokensAnnotation.class);
			sentenceAnnotation.set(
					CharacterOffsetBeginAnnotation.class,
					sentenceTokens.get(0).get(
							CharacterOffsetBeginAnnotation.class));
			sentenceAnnotation.set(
					CharacterOffsetEndAnnotation.class,
					sentenceTokens.get(sentenceTokens.size() - 1).get(
							CharacterOffsetEndAnnotation.class));
		}
		return document;
	}

	/**
	 * convert a tree t to its token representation
	 * 
	 * @param t
	 * @return
	 * @throws IOException
	 */
	public String getText(Tree t) throws IOException {
		StringBuffer sb = new StringBuffer();
		if (t == null) {
			return null;
		}
		for (Tree tt : t.getLeaves()) {
			sb.append(tt.value());
			sb.append(" ");
		}
		return sb.toString().trim();
	}

}