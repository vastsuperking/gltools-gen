package gltools.codegen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import tools.codegen.GCompilationUnit;
import tools.codegen.in.XMLReader;
import tools.codegen.out.JavaGenerator;

public class GenerateGLInterfaces {
	private static final String INPUT_DIR = "resources/Templates/GL/";
	private static final String OUTPUT_DIR = "../gltools/src/java/gltools/gl/";
			
	private static void s_output(File f) throws IOException {
		GCompilationUnit unit = new GCompilationUnit();
		XMLReader reader = new XMLReader();
		reader.setTargetUnit(unit);
		reader.setSource(s_read(f));
		reader.read();
		JavaGenerator generator = new JavaGenerator();
		generator.setSourceUnit(unit);
		
		String java = generator.generate();
		File output = new File(new File(OUTPUT_DIR), unit.getConstruct().getName() + ".java");
		s_write(java, output);
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
		if (!file.exists()) {
			if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
			file.createNewFile();
		}
		PrintWriter writer = new PrintWriter(new FileWriter(file));
		writer.print(output);
		writer.close();
	}
	public static void main(String[] args) throws IOException {
		File inputDir = new File(INPUT_DIR);
		for (File f : inputDir.listFiles()) {
			s_output(f);
		}
	}
}
