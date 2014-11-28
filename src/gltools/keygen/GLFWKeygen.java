package gltools.keygen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class GLFWKeygen {
	public static final String DOCUMENTATION_URL = "http://www.glfw.org/docs/latest/group__keys.html";
	public static final String OUTPUT_LOCATION = "./../gltools/src/resources/Config/Keyboard/glfw_keys.xml";
	
	public static void main(String[] args) throws IOException {
		StringBuilder output = new StringBuilder();
		output.append("<xml>").append('\n');
		output.append("\t<keys>").append('\n');
		
		
		Document document = s_get(DOCUMENTATION_URL);
		Element table = document.getElementsByClass("memberdecls").first().child(0);
		Elements children = table.children();
		for (Element e : children) {
			if (e.attr("class").startsWith("memitem")) {
				s_parseElement(e, output);
			}
		}
	
		output.append("\t</keys>").append('\n');
		output.append("</xml>").append('\n');
		s_write(output.toString(), new File(OUTPUT_LOCATION));
	}
	public static void s_parseElement(Element e, StringBuilder output) {
		String nameAll = e.child(1).child(0).text();
		String name = nameAll.substring("GLFW_KEY_".length(), nameAll.length());
		
		String value = s_clean(e.child(1).text()).split("\\s+")[1];
		if (value.contains("/*")) value = value.split("/*")[0];
		
		int id;
		try {
			id = Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			//Ignore it if it is not  a number
			System.out.println("Skipped: " + name);
			return;
		}
		output.append("\t\t");
		output.append("<key id=\"" + id + "\" ");
		if (name.length() == 1) {
			output.append("char=\"").append(name).append("\"");
		} else {
			output.append("name=\"").append(name).append("\"");
		}
		output.append("/>").append("\n");
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
			file.getParentFile().mkdirs();
			file.createNewFile();
		}
		PrintWriter writer = new PrintWriter(new FileWriter(file));
		writer.print(output);
		writer.close();
	}
	public static String s_clean(String input) {
		return input.replace('\u00A0', ' ');
	}
}
