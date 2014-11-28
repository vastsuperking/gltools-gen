package gltools.codegen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import tools.codegen.GArgument;
import tools.codegen.GCompilationUnit;
import tools.codegen.GConstruct;
import tools.codegen.GField;
import tools.codegen.GInterface;
import tools.codegen.GMethod;
import tools.codegen.GVisibility;
import tools.codegen.item.GConstructItem;
import tools.codegen.out.XMLGenerator;


public class GenerateGLTemplates {
	private static final String OUTPUT_DIR = "/Templates/GL/";
	//private static final String PACKAGE_SUMMARY_URL = "http://www.lwjgl.org/javadoc/org/lwjgl/opengl/package-summary.html";
	//private static final String CONSTANT_VALUES_URL = "http://www.lwjgl.org/javadoc/constant-values.html";
	private static final String CONSTANT_VALUES_URL = "file:///home/daniel/programs/java/graphics/lwjgl3/generated/javadoc/constant-values.html";
	
	//private static final String LWJGL_CLASS_URL_PREFIX = "http://lwjgl.org/javadoc/org/lwjgl/opengl/";
	private static final String LWJGL_CLASS_URL_PREFIX = "file:///home/daniel/programs/java/graphics/lwjgl3/generated/javadoc/org/lwjgl/opengl/";

	private static final String LWJGL_CLASS_URL_SUFFIX = ".html";
	private static final String[] LWJGL_CLASSES = {"GL11", "GL12", "GL13", "GL14", "GL15",
													 "GL20", "GL21",
													 "GL30", "GL31", "GL32", "GL33",
													 "GL40", "GL41", "GL42", "GL43", "GL44", "GL45"};
	public static final HashMap<String, String> s_classReplaceMap = new HashMap<String, String>();
	private HashMap<String, GField> m_constantFields = new HashMap<String, GField>();

	static {
		s_classReplaceMap.put("DEBUGPROC", "DebugCallback");
	}
	
	public void run() throws Exception {
		parseConstants(CONSTANT_VALUES_URL);
		HashMap<String, String> classURLs = parseClassListURLs();
		List<GConstruct> constructs = parseClasses(classURLs);

		File outputDir = new File("resources/" + OUTPUT_DIR);
		for (GConstruct construct : constructs) {
			File output = new File(outputDir, construct.getName() + ".template");
			System.out.println("Writing: " + output.getAbsolutePath());
			XMLGenerator generator = new XMLGenerator();
			generator.setSourceUnit(new GCompilationUnit("gltools.gl", construct));
			String xml = generator.generate();
			s_write(xml, output);
		}
	}
	private List<GConstruct> parseClasses(HashMap<String, String> classURLs) throws IOException {
		List<GConstruct> constructs = new ArrayList<GConstruct>();
		
		for (int i = 1; i <= 4; i++) {
			GInterface interfaze = new GInterface("GL" + i);
			if (i != 1) interfaze.setExtends("GL" + (i - 1));
			Set<OnlineClass> validClasses = s_getClassesNameStarts(classURLs, "GL" + i);
			
			ArrayList<GMethod> methods = new ArrayList<GMethod>();
			HashMap<String, GField> fields = new HashMap<String, GField>();
			
			for (OnlineClass onlineClass : validClasses) {
				parseClass(onlineClass.getName(), onlineClass.getURL(), fields, methods, constructs);
			}
			
			interfaze.addAll(fields.values());
			interfaze.addAll(methods);
			
			constructs.add(interfaze);
		}
		return constructs;
	}
	private void parseClass(String name, String url, HashMap<String, GField> fields, ArrayList<GMethod> methods, List<GConstruct> consts) throws IOException {
		Document doc = s_get(url);
		Element summaryContainer = doc.getElementsByClass("summary").first().child(0).child(0);
		Element fieldSummary = summaryContainer.child(0);
		Element fieldTBody = fieldSummary.getElementsByTag("tbody").first();
		Elements fieldRows = fieldTBody.getElementsByTag("tr");
		for (Element e : fieldRows) {
			String fieldName = s_clean(e.child(1).text());
			if (fieldName.equals("Field and Description")) continue;
			else fieldName = fieldName.split(" ")[0];
			if (m_constantFields.containsKey(fieldName)) {
				GField field = m_constantFields.get(fieldName);
				//If it has not already been added
				if (field.getConstruct() == null) fields.put(fieldName, field);
			}
		}
		
		Element methodSummary = summaryContainer.child(1);
		Element methodTBody = methodSummary.getElementsByTag("tbody").first();
		Elements methodRows = methodTBody.getElementsByTag("tr");
		for (Element e : methodRows) {
			String sig = s_clean(e.child(1).text());
			String methodName = sig.split("\\(")[0];
			if (methodName.equals("Method and Description")) continue;
			if (methodName.equals("getInstance")) continue;
			ArrayList<GArgument> args = s_parseArguments(sig);
			
			String[] prts = e.child(0).text().split(" ");
			String returnType = s_getClass(s_clean(prts[prts.length - 1]));
			
			
			
			GMethod method = new GMethod(GVisibility.PUBLIC, false, false, returnType, methodName);
			method.addArguments(args);
			methods.add(method);
		}
		System.out.println("Parsed: " + name + " for " + name.substring(0, name.length() - 1));
	}
	public static ArrayList<GArgument> s_parseArguments(String functionDeclaration) {
		String[] splitStartingParenth = functionDeclaration.split("\\(");
		if (splitStartingParenth.length < 2) return new ArrayList<GArgument>(); 
		String[] splitEndingParenth = splitStartingParenth[1].split("\\)");
		if (splitEndingParenth.length == 0) return new ArrayList<GArgument>();
		String argumentsSeparated = splitEndingParenth[0];

		String[] arguments = argumentsSeparated.split(",");
		
		ArrayList<GArgument> argumentList = new ArrayList<GArgument>(arguments.length);
		for (int i = 0; i < arguments.length; i++) {
			String arg = arguments[i].trim();
			if (arg.equals("") || arg == null) continue;
			String[] parts = arg.split("\\s+");
			String type = s_getClass(parts[0]);
			
			String name = parts[1];
			argumentList.add(new GArgument(type, name));
		}
		return argumentList;
	}
	private HashMap<String, String> parseClassListURLs() {
		HashMap<String, String> classURLs = new HashMap<String, String>();
		for (String clazz : LWJGL_CLASSES) {
			String url = LWJGL_CLASS_URL_PREFIX + clazz + LWJGL_CLASS_URL_SUFFIX;
			classURLs.put(clazz, url);
		}
		return classURLs;
	}
	private void parseConstants(String constantsURL) throws IOException {
		Document doc = s_get(constantsURL);
		Element constantsContainer = doc.getElementsByClass("constantValuesContainer").first();
		Elements blockLists = constantsContainer.children();
		for (Element bl : blockLists) {
			if (!bl.attr("class").equals("blockList")) continue;
			Elements innerBlockLists = bl.children();
			for (Element ibl : innerBlockLists) {
				if (!ibl.attr("class").equals("blockList")) continue;
				
				Element table = ibl.child(0);
				System.out.println("Parsing: " + table.child(0).text());
				//Get main table body(3rd element, 2nd index)
				Element tBody = table.child(2);
				//Get all table rows from table body
				Elements rows = tBody.getElementsByTag("tr");
				for (Element row : rows) processConstant(row);
			}
		}
	}
	private void processConstant(Element row) {
		String modsAndType = s_clean(row.getAllElements().get(0).text());
		String[] modsParts = modsAndType.split(" ");
		String value = modsParts[modsParts.length - 1];
		String name = modsParts[modsParts.length - 2];
		String type = modsParts[modsParts.length - 3];
		
		boolean isStatic = modsAndType.contains("static");
		boolean isFinal = modsAndType.contains("final");		
		if (s_isInt(value)) {
			int intVal = Integer.parseInt(value);
			String formattedValue = "0x" + String.format("%05X", intVal & 0xFFFFFF);
			GField field = new GField(GVisibility.PUBLIC, isStatic, isFinal, type, name, formattedValue);
			m_constantFields.put(name, field);
		}
	}
	public static Set<OnlineClass> s_getClassesNameStarts(HashMap<String, String> map, String start) {
		Set<OnlineClass> set = new TreeSet<OnlineClass>();
		for (Entry<String, String> e : map.entrySet()) {
			if (e.getKey().startsWith(start)) set.add(new OnlineClass(e.getKey(), e.getValue()));
		}
		return set;
	}
	public static Document s_get(String url) throws IOException {
		if (url.startsWith("file")) {
			try {
				return Jsoup.parse(s_read(new File(new URL(url).toURI())));
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			return null;
		} else {
			HttpGet get = new HttpGet(url);
			HttpResponse response = HttpClients.createDefault().execute(get);
			String html = EntityUtils.toString(response.getEntity());
			return Jsoup.parse(html);
		}
    }
	
	public static String s_getClass(String clazz) {
		if (s_classReplaceMap.containsKey(clazz)) return s_classReplaceMap.get(clazz);
		else return clazz;
	}
	
	public static String s_clean(String input) {
		return input.replace('\u00A0', ' ');
	}
	public static boolean s_isInt(String str) {
	    try { 
	        Integer.parseInt(str); 
	    } catch(NumberFormatException e) { 
	        return false; 
	    }
	    // only got here if we didn't return false
	    return true;
	}
	private static String s_read(File f) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(f));
		StringBuilder builder = new StringBuilder();
		
		String line;
		while((line = reader.readLine()) != null) {
			builder.append(line).append('\n');
		}
		reader.close();
		
		return builder.toString();
	}
	public static void s_write(String output, File file) throws IOException {
		PrintWriter writer = new PrintWriter(new FileWriter(file));
		writer.print(output);
		writer.close();
	}
	public static void main(String[] args) throws Exception {
		GenerateGLTemplates gen = new GenerateGLTemplates();
		gen.run();
	}
	public static class OnlineClass implements Comparable<OnlineClass> {
		private String m_name;
		private String m_url;
		
		public OnlineClass(String name, String url) {
			m_name = name;
			m_url = url;
		}
		public String getName() { return m_name; }
		public String getURL() { return m_url; }
		@Override
		public int compareTo(OnlineClass o) {
			return m_name.compareTo(o.getName());
		}

	}
}
