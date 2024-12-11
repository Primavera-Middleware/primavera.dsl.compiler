import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList; 
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gerador {

    static class ClassField {
        String type;
        String name;
    }

    static class InnerClass {
        String name;
        List<ClassField> fields = new ArrayList<>();
    }

    static class MethodDefinition {
        String returnType;
        String methodName;
        String httpMethod;
        String route;
        String parameters; 
        String queryParam; 
        String pathParam;   
    }

    public static void main(String[] args) {
        String inputFile = "C:\\Users\\pablo\\Documents\\Primavera\\primavera.dsl.compiler\\src\\example.dsl";
        String server = "";
        String mainClassName = "";
        String packageName = "";
        List<InnerClass> innerClasses = new ArrayList<>();
        List<MethodDefinition> methods = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line;
            Pattern pPackage = Pattern.compile("^package\\s+([a-zA-Z0-9_.]+);");
            Pattern pServer = Pattern.compile("server\\s*=\\s*\"([^\"]+)\";");
            Pattern pClass = Pattern.compile("class\\s+(\\w+)\\s*\\{");
            Pattern pInnerClass = Pattern.compile("class\\s+(\\w+)\\s*\\{");
            Pattern pField = Pattern.compile("type\\s+(\\w+)\\s+(\\w+);");
            Pattern pMethod = Pattern.compile("method\\s+(\\w+)\\s+(\\w+)\\(([^)]*)\\)\\s+(GET|POST|PUT|DELETE)\\s+route\\s*=\\s*\"([^\"]+)\";");
            Pattern pQueryParam = Pattern.compile("query\\s+param\\s*=\\s*\\(([^=]+)=([^\\)]+)\\)");
            Pattern pPathParam = Pattern.compile("path\\s+param\\s*=\\s*\\(([^=]+)=([^\\)]+)\\)");

            boolean insideMainClass = false;
            boolean insideInnerClass = false;
            InnerClass currentInnerClass = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                Matcher pkgM = pPackage.matcher(line);
                if (pkgM.find()) {
                    packageName = pkgM.group(1);
                }

                if (line.startsWith("server =")) {
                    Matcher m = pServer.matcher(line);
                    if (m.find()) {
                        server = m.group(1);
                    }
                } else if (line.startsWith("class ")) {
                    Matcher mc = pClass.matcher(line);
                    if (mc.find() && !insideMainClass) {
                        // main class
                        mainClassName = mc.group(1);
                        insideMainClass = true;
                        continue;
                    }

                    Matcher mic = pInnerClass.matcher(line);
                    if (mic.find() && insideMainClass) {
                        insideInnerClass = true;
                        currentInnerClass = new InnerClass();
                        currentInnerClass.name = mic.group(1);
                        innerClasses.add(currentInnerClass);
                    }
                } else if (insideInnerClass) {
                    if (line.startsWith("}")) {
                        insideInnerClass = false;
                        currentInnerClass = null;
                    } else {
                        Matcher mf = pField.matcher(line);
                        if (mf.find()) {
                            ClassField f = new ClassField();
                            f.type = mf.group(1);
                            f.name = mf.group(2);
                            currentInnerClass.fields.add(f);
                        }
                    }
                } else if (line.startsWith("method ")) {
                    Matcher m = pMethod.matcher(line);
                    if (m.find()) {
                        MethodDefinition md = new MethodDefinition();
                        md.returnType = m.group(1);
                        md.methodName = m.group(2);
                        md.parameters = m.group(3);
                        md.httpMethod = m.group(4);
                        md.route = m.group(5);
                        methods.add(md);
                    }
                } else if (line.startsWith("query param")) {
                    if (!methods.isEmpty()) {
                        MethodDefinition md = methods.get(methods.size()-1);
                        Matcher m = pQueryParam.matcher(line);
                        if (m.find()) {
                            md.queryParam = m.group(1) + "=" + m.group(2);
                        }
                    }
                } else if (line.startsWith("path param")) {
                    if (!methods.isEmpty()) {
                        MethodDefinition md = methods.get(methods.size()-1);
                        Matcher m = pPathParam.matcher(line);
                        if (m.find()) {
                            md.pathParam = m.group(1) + "=" + m.group(2);
                        }
                    }
                }
            }

            generateJavaClass(packageName, mainClassName, server, innerClasses, methods);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateJavaClass(String packageName, String mainClassName, String server, List<InnerClass> innerClasses, List<MethodDefinition> methods) {
        if (mainClassName == null || mainClassName.isEmpty()) {
            mainClassName = "Service";
        }
        String className = mainClassName + "Client";

        try (FileWriter fw = new FileWriter(className + ".java")) {
            if (packageName != null && !packageName.isEmpty()) {
                fw.write("package " + packageName + ";\n\n");
            }
                      
            fw.write("import java.io.IOException;\n");
            fw.write("import java.net.URISyntaxException;\n");
            
            fw.write("import br.ufrn.imd.primavera.remoting.handlers.client.ClientRequestHandler;\n");
            fw.write("import br.ufrn.imd.primavera.remoting.handlers.client.Request;\n");
            fw.write("import br.ufrn.imd.primavera.remoting.handlers.client.Response;\n\n");

            fw.write("public class " + className + " {\n\n");
            fw.write("    private final String baseUrl;\n");
            fw.write("    private final ClientRequestHandler clientRequestHandler;\n\n");
            fw.write("    public " + className + "() {\n");
            fw.write("        this.baseUrl = \"http://" + server + "\";\n");
            fw.write("        this.clientRequestHandler = new ClientRequestHandler();\n");
            fw.write("    }\n\n");

            for (InnerClass ic : innerClasses) {
                fw.write("    public static class " + ic.name + " {\n");
                for (ClassField f : ic.fields) {
                    String javaType = mapType(f.type);
                    fw.write("        public " + javaType + " " + f.name + ";\n");
                }
                fw.write("    }\n\n");
            }

            for (MethodDefinition md : methods) {
                String javaParams = convertParams(md.parameters);
                String[] paramNames = extractParamNames(javaParams);

                fw.write("    public Response " + md.methodName + "(" + javaParams + ") throws IOException, InterruptedException, URISyntaxException {\n");

                String processedRoute = processRoute(md.route, paramNames, md.pathParam);
                fw.write("        String path = \"" + processedRoute + "\";\n");
                fw.write("        Request request = new Request(\"" + md.httpMethod + "\", baseUrl, path);\n");

                if (md.queryParam != null && !md.queryParam.isEmpty()) {
                    String[] qp = md.queryParam.split("=");
                    String qpKey = qp[0].trim();
                    String qpVal = qp[1].trim();
                    String qpValReplaced = replaceParamValue(qpVal, paramNames);
                    fw.write("        request.addQueryParam(\"" + qpKey + "\", " + qpValReplaced + ");\n");
                }

                if (md.httpMethod.equalsIgnoreCase("POST") || md.httpMethod.equalsIgnoreCase("PUT")) {
                    String jsonBody = buildJsonBody(paramNames);
                    fw.write("        request.addHeader(\"Content-Type\", \"application/json\");\n");
                    fw.write("        request.setBody(" + jsonBody + ");\n");
                }

                fw.write("        Response response = clientRequestHandler.sendRequest(request);\n");
                fw.write("        if (response.getStatusCode() == 200 || response.getStatusCode() == 201) {\n");
                fw.write("            return response;\n");
                fw.write("        } else {\n");
                fw.write("            throw new RuntimeException(\"Falha ao chamar " + md.methodName + ": Codigo HTTP \" + response.getStatusCode());\n");
                fw.write("        }\n");
                fw.write("    }\n\n");
            }

            fw.write("}\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String mapType(String t) {
        if (t.equals("String")) return "String";
        if (t.equals("int")) return "int";
        if (t.equals("Integer")) return "Integer";
        return "String"; 
    }

    private static String convertParams(String parameters) {
        if (parameters == null || parameters.trim().isEmpty()) return "";
        String[] parts = parameters.split(",");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            p = p.trim();
            String[] tokens = p.split("\\s+");
            if (tokens.length == 2) {
                String t = tokens[0];
                String n = tokens[1];
                if (t.equals("fields") || t.equals("type")) {
                    t = "String";
                }
                if (sb.length() > 0) sb.append(", ");
                sb.append(mapType(t)).append(" ").append(n);
            } else {
                String n = tokens[tokens.length-1];
                if (sb.length() > 0) sb.append(", ");
                sb.append("String ").append(n);
            }
        }
        return sb.toString();
    }

    private static String[] extractParamNames(String javaParams) {
        if (javaParams.trim().isEmpty()) return new String[0];
        String[] parts = javaParams.split(",");
        String[] names = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] tokens = parts[i].trim().split("\\s+");
            names[i] = tokens[tokens.length - 1];
        }
        return names;
    }

    private static String processRoute(String route, String[] paramNames, String pathParam) {
        if (pathParam != null) {
            String[] pp = pathParam.split("=");
            String paramName = pp[0].trim();
            String routeVar = pp[1].trim();
            route = route.replace("{" + routeVar + "}", "\" + " + paramName + " + \"");
        }
        return route;
    }

    private static String replaceParamValue(String val, String[] paramNames) {
        for (String p : paramNames) {
            if (p.equals(val)) {
                return p;
            }
        }
        return "\"" + val + "\"";
    }

    private static String buildJsonBody(String[] paramNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"{");
        for (int i = 0; i < paramNames.length; i++) {
            sb.append("\\\"").append(paramNames[i]).append("\\\": \\\"\" + ").append(paramNames[i]).append(" + \"\\\"");
            if (i < paramNames.length - 1) sb.append(", ");
        }
        sb.append("}\"");
        return sb.toString();
    }
}
