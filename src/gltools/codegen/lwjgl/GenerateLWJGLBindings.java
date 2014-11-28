package gltools.codegen.lwjgl;

import gltools.codegen.GenerateGLTemplates;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import tools.codegen.GArgument;
import tools.codegen.GClass;
import tools.codegen.GCodeFragment;
import tools.codegen.GCompilationUnit;
import tools.codegen.GImport;
import tools.codegen.GMethod;
import tools.codegen.GVisibility;
import tools.codegen.in.XMLReader;
import tools.codegen.item.GConstructItem;
import tools.codegen.out.JavaGenerator;

public class GenerateLWJGLBindings {
	//private static final String LWJGL_CLASS_URL_PREFIX = "http://lwjgl.org/javadoc/org/lwjgl/opengl/";
	private static final String LWJGL_CLASS_URL_PREFIX = "file:///home/daniel/programs/java/graphics/lwjgl3/generated/javadoc/org/lwjgl/opengl/";
	private static final String LWJGL_CLASS_URL_SUFFIX = ".html";
	private static final String[] LWJGL_CLASSES = {"GL11", "GL12", "GL13", "GL14", "GL15",
													 "GL20", "GL21",
													 "GL30", "GL31", "GL32", "GL33",
													 "GL40", "GL41", "GL42", "GL43", "GL44", "GL45"};
	private static final String TEMPLATE_LOC = "resources/Templates/GL";
	private static final String OUTPUT_DIR = "../gltools-lwjgl/src/gltools/gl/lwjgl";
	
	private static final HashMap<String, String> s_importMap = new HashMap<String, String>();
	private static final HashSet<String> s_classesToWrap = new HashSet<String>();

	static {
		s_importMap.put("PointerBuffer", "gltools.gl.PointerBuffer");
		s_importMap.put("Pointer", "gltools.gl.Pointer");
		s_importMap.put("DebugCallback", "gltools.gl.DebugCallback");
		s_importMap.put("GL1", "gltools.gl.GL1");
		s_importMap.put("GL2", "gltools.gl.GL2");
		s_importMap.put("GL3", "gltools.gl.GL3");
		s_importMap.put("GL4", "gltools.gl.GL4");
		
		s_classesToWrap.add("PointerBuffer");
		s_classesToWrap.add("Pointer");
		s_classesToWrap.add("DebugCallback");
	}
	
	private HashMap<String, String> m_functionMap = new HashMap<String, String>();
	
	public void run() throws IOException {
		parseFunctionMap();
		generateGLs();
	}
	
	public void generateGLs() throws IOException {
		File inputDir = new File(TEMPLATE_LOC);
		for (File f : inputDir.listFiles()) {
			GCompilationUnit unit = generate(f);
			//		new GCompilationUnit("gltools.gl.lwjgl", construct);
			for (String clazz : LWJGL_CLASSES) {
				if (clazz.startsWith(unit.getConstruct().getName().substring(5))) {
					unit.addImport(new GImport("org.lwjgl.opengl." + clazz));
				}
			}
			
			JavaGenerator generator = new JavaGenerator();
			generator.setSourceUnit(unit);
			
			String java = generator.generate();
			File output = new File(new File(OUTPUT_DIR), unit.getConstruct().getName() + ".java");
			s_write(java, output);
		}
	}
	public GCompilationUnit generate(File f) throws IOException {
		GCompilationUnit template = new GCompilationUnit();
		XMLReader reader = new XMLReader();
		reader.setTargetUnit(template);
		reader.setSource(s_read(f));
		reader.read();
		
		
		int glVersion = Integer.parseInt(template.getConstruct().getName().substring(2));
		
		GClass clazz = new GClass("LWJGL" + template.getConstruct().getName());
		if (glVersion != 1) clazz.setExtends("LWJGLGL" + (glVersion - 1));
		clazz.addImplements(template.getConstruct().getName());
		
		GCompilationUnit unit = new GCompilationUnit("gltools.gl.lwjgl", clazz);

		unit.addImport(new GImport(s_importMap.get(template.getConstruct().getName())));
		
		for (GConstructItem item : template.getConstruct().getItems()) {
			if (item instanceof GMethod) {
				GMethod mI = (GMethod) item;
				GMethod method = new GMethod(GVisibility.PUBLIC, mI.getName());
				method.addArguments(mI.getArguments());
				method.setReturnType(mI.getReturnType());
				//Create body
				StringBuilder body = new StringBuilder();
					
				StringBuilder call = new StringBuilder();
				
				String declaration = s_dec(method.getReturnType(), method.getName(), method.getArguments());
				String source = m_functionMap.get(declaration);
				
				call.append(source).append(".");
				call.append(method.getName()).append('(');
				for (int i = 0; i < method.getArguments().size(); i++) {
					GArgument arg = method.getArgument(i);
					//If it is not a primitive and does not contain full path (.), add the import here
					if (!s_isPrimitive(arg.getType()) && s_importMap.containsKey(arg.getType())) {
						//Add import
						unit.addImport(new GImport(s_importMap.get(arg.getType())));
					}
					if (!s_classesToWrap.contains(arg.getType())) {
						call.append(arg.getName());
					} else {
						call.append(s_wrap(arg.getType(), arg.getName()));
					}
					if (i != method.getArguments().size() - 1)
						call.append(',').append(' ');
				}
				call.append(")");
				
				if (!mI.getReturnType().equals("void")) {
					body.append("return ");
					if (s_classesToWrap.contains(mI.getReturnType())) {
						body.append(s_unwrap(mI.getReturnType(), call.toString()));
					} else {
						body.append(call.toString());
					}
				} else {
					body.append(call.toString());
				}
				body.append(';');
				
				method.setBody(new GCodeFragment(body.toString()));
				clazz.add(method);
			}
		}
		return unit;
	}
	public void parseFunctionMap() throws IOException {
		HashMap<String, String> classes = parseClassListURLs();
		for (Entry<String, String> clazz : classes.entrySet()) {
			parseClass(clazz.getKey(), clazz.getValue());
		}
	}
	private void parseClass(String name, String url) throws IOException {
		Document doc = s_get(url);
		Element summaryContainer = doc.getElementsByClass("summary").first().child(0).child(0);

		Element methodSummary = summaryContainer.child(1);
		Element methodTBody = methodSummary.getElementsByTag("tbody").first();
		Elements methodRows = methodTBody.getElementsByTag("tr");
		for (Element e : methodRows) {
			String sig = s_clean(e.child(1).text());
			String methodName = sig.split("\\(")[0];
			if (methodName.equals("Method and Description")) continue;
			ArrayList<GArgument> args = s_parseArguments(sig);
			
			String[] prts = e.child(0).text().split(" ");
			String returnType = s_getClassLWJGL(s_clean(prts[prts.length - 1]));
			
			String dec = s_dec(returnType, methodName, args);
			
			System.out.println("Mapping: " + dec + " to " + name);
			m_functionMap.put(dec, name);
		}
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
			String type = s_getClassLWJGL(parts[0]);
			
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

	public static void main(String[] args) throws IOException {
		new GenerateLWJGLBindings().run();
	}
	public static String s_getClassLWJGL(String clazz) {
		if (GenerateGLTemplates.s_classReplaceMap.containsKey(clazz)) return GenerateGLTemplates.s_classReplaceMap.get(clazz);
		else return clazz;
	}
	public static String s_dec(String returnType, String methodName, List<GArgument> args) {
		String dec = returnType + " " + methodName + "(";
		for (int i = 0; i < args.size(); i++) {
			GArgument arg = args.get(i);
			dec += arg.getType() + " " + arg.getName();
			if (i != args.size() - 1) {
				dec += ", ";
			}
		}
		dec += ");";
		return dec;
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
	public static String s_wrap(String clazz, String value) {
		return "LWJGLUtils.s_wrap" + clazz + "(" + value + ")";
	}
	public static String s_unwrap(String clazz, String value) {
		return "LWJGLUtils.s_unwrap" + clazz + "(" + value + ")";
	}
	public static String s_clean(String input) {
		return input.replace('\u00A0', ' ');
	}
	public static boolean s_isPrimitive(String type) {
		return type.equals("short") || type.equals("int") || type.equals("long")
			 || type.equals("float") || type.equals("double")
			 || type.equals("boolean");
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
}
