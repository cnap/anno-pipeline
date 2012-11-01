//
// These are the tools used to annotate Annotated Gigaword (Napoles, 
// Gormley, and Van Durme, 2012) using a modified Stanford CoreNLP 
// pipeline. The current version here is StanfordCoreNLP v.1.3.2. The 
// primary modification is to use parse trees as input (instead of 
// parsing sentences using the Stanford parser/grammar). The pipeline 
// is also modified to print the root dependency.
//
// GigawordAnnotator takes a Gigaword-style file with parse trees and
// creates annotations using the Stanford CoreNLP tools
//
// Courtney Napoles, cdnapoles@gmail.com
// 2012-07-03

package edu.jhu.annotation;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Serializer;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;

public class GigawordAnnotator {
	StanfordCoreNLP pipeline;
	GigawordDocumentHandler gdh;
	static final String usage = "You must specify an input path: java edu.jhu.annotation.GigawordAnnotator --in path/to/inputfile\n"
			+ "  Optional arguments: \n"
			+ "       --sgml t|f                 input has SGML markup (default: t)\n"
			+ "       --text \"TEXT_TYPE\"         name of the type contaning parses (default: TEXT)\n"
			+ "       --doc \"DOC_TYPE\"           name of the parent of TEXT_TYPE (default: DOC)\n"
			+ "       --parsePrefix \"(ROOT (\"    prefix starting parses (default: \"( (\")\n"
			+ "       --ner t|f                  perform NER (default:t)\n"
			+ "       --coref t|f                perform coref resolution (default: t)\n"
			+ "       --dep t|f                  extract dependencies (default: t)\n"
	                + "       --sents t|f                just sentences, no document structure (default f; if true no coref and no SGML input)\n"
			+ "       --debug                 to print debugging messages";

	String docTypeName = "DOC";
	String textTypeName = "TEXT";
	String parsePrefix = "( (";
	boolean do_ner = true;
	boolean do_coref = true;
	boolean do_deps = true;
	boolean useSGML = true;
	boolean debug = false;
    boolean justSents = false;
	String inputpath = null;

	public GigawordAnnotator() {}

	// desired options: specify output path,specify SGML markup,parse prefix
	// (default "( ("),DOC entity name, TEXT entity name, only print specific
	// docids, do NER, do coref, do dependencies
	//
	public static void main(String[] args) {
		GigawordAnnotator gigannotator = new GigawordAnnotator();
		gigannotator.processArgs(args);
		gigannotator.initialize();
		try {
			gigannotator.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initialize() {
		gdh = new GigawordDocumentHandler(inputpath);
		gdh.setOptions(useSGML, textTypeName, docTypeName, parsePrefix, debug, justSents);
		Properties props = new Properties();
		String annotatorList = "tokenize, ssplit, pos, lemma, parse";
		if (do_coref)
			annotatorList += ", ner, dcoref";
		else if (do_ner)
			annotatorList += ", ner";
		if (debug) {
			System.err.println("Using annotators " + annotatorList);
		}
		props.put("annotators", annotatorList);
		pipeline = new StanfordCoreNLP(props);
	}

	private void processArgs(String[] args) {
		int i = 0;
		try {
			while (i < args.length) {
				if (args[i].equals("--in")) {
					inputpath = args[++i];
				} else if (args[i].equals("--sgml")) {
					if (args[++i].equalsIgnoreCase("f")) {
						useSGML = false;
					}
				} else if (args[i].equals("--text")) {
					textTypeName = args[++i];
				} else if (args[i].equals("--doc")) {
					docTypeName = args[++i];
				} else if (args[i].equals("--parsePrefix")) {
					parsePrefix = args[++i];
				} else if (args[i].equals("--dep")) {
					if (args[++i].equalsIgnoreCase("f")) {
						do_deps = false;
					}
				} else if (args[i].equals("--ner")) {
					if (args[++i].equalsIgnoreCase("f")) {
						do_ner = false;
					}
				} else if (args[i].equals("--coref")) {
					if (args[++i].equalsIgnoreCase("f")) {
						do_coref = false;
					}
				} else if (args[i].equals("--debug")) {
					debug = true;
				} else if (args[i].equals("--sents")) {
					if (args[++i].equalsIgnoreCase("t")) {
					    justSents = true;
					    do_coref = false;
					    useSGML = false;
					}
				}
				else {
					System.err.println("Invalid option: " + args[i]);
					System.err.println(usage);
					System.exit(1);
				}
				i++;
			}
		} catch (Exception e) {
			System.err.println(usage);
			System.exit(1);

		}
		if (inputpath == null) {
			System.err.println(usage);
			System.exit(1);
		}
	}

	/**
	 * path contains a list of document ids if you only want to annotate certain
	 * documents (assumes ordered ids)
	 * 
	 * @param path
	 */
	public void setDocList(String path) {
		try {
			gdh.setDocList(path);
		} catch (IOException e) {
			System.err.println("Error loading document ids from " + path);
			e.printStackTrace();
		}
	}

	/**
	 * sequentially annotates documents from the GigawordDocumentHandler
	 * 
	 * @throws IOException
	 */
	public void run() throws IOException {
		gdh.openReader();
		while (!gdh.fileEmpty()) {
				Annotation document = gdh.getNextDocumentAnnotation();
				if (debug) { System.err.println("Annotating document "+gdh.currentDocument); }
				if (document == null) {
					if (debug) {
						System.err.println("Null document");
					}
					continue;
				}
				if (debug) {
					System.err.println("Annotating morphology");
				}
				try {
				    morphaAnnotator().annotate(document);
				} catch (Exception e) {
				    System.err.println("Error annotating morphology of " + gdh.currentDocument);
				    if (debug) 
					e.printStackTrace();
				}
				if (do_ner) {
					if (debug) {
						System.err.println("Annotating NER");
					}
					try {
					    nerAnnotator().annotate(document);
					} catch (Exception e) {
					    System.err.println("Error annotating NEs of " + gdh.currentDocument);
					    if (debug) 
						e.printStackTrace();
					}
				}
				if (do_coref) {
					if (debug) {
						System.err.println("Annotating coref");
					}
					fixNullDependencyGraphs(document);
					try {
					    dcorefAnnotator().annotate(document);
					} catch (Exception e) {
					    System.err.println("Error annotating coref chains of " + gdh.currentDocument);
					    if (debug) 
						e.printStackTrace();
					}
				}
				stanfordPrintXML(document);
		}
		gdh.closeReader();
	}

    /**
     * sentences with no dependency structure have null values for the various
     * dependency annotations. make sure these are empty dependencies instead
     * to prevent coref-resolution from dying
     **/
    public void fixNullDependencyGraphs(Annotation anno) {
        for (CoreMap sent : anno.get(SentencesAnnotation.class)) {
            if (sent.get(CollapsedDependenciesAnnotation.class) == null) {
                sent.set(CollapsedDependenciesAnnotation.class, new SemanticGraph());
            }
	}
    }


	/**
	 * add dependency relations to the XML. adapted from StanfordCoreNLP to add
	 * root dependency and change format
	 * 
	 * @param semGraph
	 *            the dependency graph
	 * @param parentElem
	 *            the element to attach dependency info to
	 */
	public void addDependencyToXML(SemanticGraph semGraph, Element parentElem) {
		if (semGraph != null && semGraph.edgeCount() > 0) {
			Element rootElem = new Element("dep");
			rootElem.addAttribute(new Attribute("type", "root"));
			Element rootGovElem = new Element("governor");
			rootGovElem.appendChild("0");
			rootElem.appendChild(rootGovElem);
			// need to surround this in a try/catch in case there is no 
			// root in the dependency graph
			try { 
			    String rootIndex = Integer.toString(semGraph.getFirstRoot()
								.get(IndexAnnotation.class));
			    Element rootDepElem = new Element("dependent");
			    rootDepElem.appendChild(rootIndex);
			    rootElem.appendChild(rootDepElem);
			    parentElem.appendChild(rootElem);
			}
			catch (Exception e) {}
			for (SemanticGraphEdge edge : semGraph.edgeListSorted()) {
				String rel = edge.getRelation().toString();
				rel = rel.replaceAll("\\s+", "");
				int source = edge.getSource().index();
				int target = edge.getTarget().index();

				Element depElem = new Element("dep");
				depElem.addAttribute(new Attribute("type", rel));

				Element govElem = new Element("governor");
				govElem.appendChild(Integer.toString(source));
				depElem.appendChild(govElem);

				Element dependElem = new Element("dependent");
				dependElem.appendChild(Integer.toString(target));
				depElem.appendChild(dependElem);

				parentElem.appendChild(depElem);
			}
		}
	}

	/**
	 * Create the XML document, using the base StanfordCoreNLP default and
	 * adding custom dependency representations (to include root elements)
	 * 
	 * @param anno
	 *            Document to be output as XML
	 * @throws IOException
	 */
	public void stanfordPrintXML(Annotation anno) throws IOException {
		Document xmlDoc = pipeline.annotationToDoc(anno);
		Element root = xmlDoc.getRootElement();
		Element docElem = (Element) root.getChild(0);

		// because StanfordCoreNLP.annotationToDoc() only appends the coref
		// element if it is nonempty (per Ben's request)
		if (docElem.getFirstChildElement("coreference") == null) {
			Element corefInfo = new Element("coreference", null);
			docElem.appendChild(corefInfo);
		}

		if (do_deps) {
			if (debug) {
				System.err.println("Annotating dependencies");
			}

			// add dependency annotations (need to do it this way because
			// CoreNLP
			// does not include root annotation, and format is different from
			// AnnotatedGigaword)
			for (CoreMap sentence : anno.get(SentencesAnnotation.class)) {
			    try {
				ParserAnnotatorUtils.fillInParseAnnotations(false, sentence,
						sentence.get(TreeAnnotation.class));
			    } catch (Exception e) {
				if (debug) {
				    System.err.println("Error filling in parse annotation for sentence "+sentence);
				}
			    }

			}
			List<CoreMap> sentences = anno
					.get(CoreAnnotations.SentencesAnnotation.class);
			Elements sentElems = docElem.getFirstChildElement("sentences")
					.getChildElements("sentence");
			for (int i = 0; i < sentElems.size(); i++) {
				Element thisSent = sentElems.get(i);
				thisSent.addAttribute(new Attribute("id",""+sentences.get(i).get(SentenceIndexAnnotation.class))); 
				Element basicDepElem = thisSent
						.getFirstChildElement("basic-dependencies");
				SemanticGraph semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
				addDependencyToXML(semGraph, basicDepElem);
				Element colDepElem = thisSent.getFirstChildElement("collapsed-dependencies");
				semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
				addDependencyToXML(semGraph, colDepElem);
				Element colCcDepElem = thisSent.getFirstChildElement("collapsed-ccprocessed-dependencies");
				semGraph = sentences.get(i).get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
				addDependencyToXML(semGraph, colCcDepElem);
			}
		}
		// print to standard out
		Serializer ser = new Serializer(System.out, "UTF-8");
		ser.setIndent(2);
		ser.setMaxLength(0);
		Elements docElements = docElem.getChildElements();
		if (justSents) {
		    Elements sentElems = docElem.getFirstChildElement("sentences")
			.getChildElements("sentence");
		    for (int i = 0; i < sentElems.size(); i++) {
			ser.write(sentElems.get(i));
			ser.writeRaw("\n");
		    }

		}
		else {
		    for (int i = 0; i < docElements.size(); i++) {
			ser.write(docElements.get(i));
			ser.writeRaw("\n");
		    }
		}
		ser.flush();
		if (useSGML) {
			System.out.println("\n</" + gdh.docTypeName + ">");
		}
	}

	// return various annotators from the CoreNLP tools
	public Annotator morphaAnnotator() {
		return StanfordCoreNLP.getExistingAnnotator("lemma");
	}

	public Annotator nerAnnotator() {
		return StanfordCoreNLP.getExistingAnnotator("ner");
	}

	public Annotator dcorefAnnotator() {
		return StanfordCoreNLP.getExistingAnnotator("dcoref");
	}
}