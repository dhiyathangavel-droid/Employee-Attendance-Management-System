// ...existing code...
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import com.sun.net.httpserver.*;

class Employee {
    final int id;
    String name;
    String department;

    Employee(int id, String name, String department) {
        this.id = id;
        this.name = name;
        this.department = department;
    }

    @Override
    public String toString() {
        return id + " | " + name + " | " + department;
    }

    String toCsv() {
        return id + "," + csvEscape(name) + "," + csvEscape(department);
    }

    static String csvEscape(String s) {
        if (s == null) return "";
        String out = s.replace("\"", "\"\"");
        return out.contains(",") ? "\"" + out + "\"" : out;
    }
}

class AttendanceRecord {
    final int employeeId;
    final LocalDate date;
    final String status;
    final String notes;

    AttendanceRecord(int employeeId, LocalDate date, String status, String notes) {
        this.employeeId = employeeId;
        this.date = date;
        this.status = status;
        this.notes = notes;
    }

    String toCsv() {
        return employeeId + "," + date + "," + Employee.csvEscape(status) + "," + Employee.csvEscape(notes);
    }
}

class EmployeeRepository {
    private final Path employeesFile = Paths.get("employees.csv");
    private final Path attendanceFile = Paths.get("attendance.csv");
    private final Map<Integer, Employee> employees = new LinkedHashMap<>();
    private final List<AttendanceRecord> attendance = new ArrayList<>();
    private int nextId = 1;

    EmployeeRepository() {
        loadEmployees();
        loadAttendance();
    }

    private void loadEmployees() {
        if (!Files.exists(employeesFile)) return;
        try (BufferedReader r = Files.newBufferedReader(employeesFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = splitCsv(line, 3);
                if (parts.length >= 3) {
                    try {
                        int id = Integer.parseInt(parts[0].trim());
                        String name = parts[1].trim();
                        String dept = parts[2].trim();
                        employees.put(id, new Employee(id, name, dept));
                        if (id >= nextId) nextId = id + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load employees: " + e.getMessage());
        }
    }

    private void loadAttendance() {
        if (!Files.exists(attendanceFile)) return;
        try (BufferedReader r = Files.newBufferedReader(attendanceFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = splitCsv(line, 4);
                if (parts.length >= 4) {
                    try {
                        int id = Integer.parseInt(parts[0].trim());
                        LocalDate d = LocalDate.parse(parts[1].trim());
                        String status = parts[2].trim();
                        String notes = parts[3].trim();
                        attendance.add(new AttendanceRecord(id, d, status, notes));
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load attendance: " + e.getMessage());
        }
    }

    List<Employee> listEmployees() {
        return new ArrayList<>(employees.values());
    }

    Optional<Employee> findEmployeeById(int id) {
        return Optional.ofNullable(employees.get(id));
    }

    Employee addEmployee(String name, String department) {
        Employee e = new Employee(nextId++, name, department);
        employees.put(e.id, e);
        saveEmployees();
        return e;
    }

    void recordAttendance(int empId, LocalDate date, String status, String notes) {
        AttendanceRecord rec = new AttendanceRecord(empId, date, status, notes);
        attendance.add(0, rec);
        appendLine(attendanceFile, rec.toCsv());
    }

    List<AttendanceRecord> listAttendanceForDate(LocalDate date) {
        return attendance.stream().filter(r -> r.date.equals(date)).collect(Collectors.toList());
    }

    List<AttendanceRecord> allAttendance() {
        return new ArrayList<>(attendance);
    }

    private void saveEmployees() {
        try (BufferedWriter w = Files.newBufferedWriter(employeesFile)) {
            for (Employee e : employees.values()) w.write(e.toCsv() + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Failed to save employees: " + e.getMessage());
        }
    }

    private void appendLine(Path file, String line) {
        try {
            Files.write(file, (line + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to append to " + file + ": " + e.getMessage());
        }
    }

    private static String[] splitCsv(String line, int expected) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++; continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        while (parts.size() < expected) parts.add("");
        return parts.toArray(new String[0]);
    }
}

public class Employee Management System {
    private final Scanner scanner = new Scanner(System.in);
    final EmployeeRepository repo = new EmployeeRepository();

    public static void main(String[] args) throws Exception {
        EmployeeManagementSystem app = new EmployeeManagementSystem();
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            WebServer.startServer(8080, app.repo);
        } else {
            app.runConsole();
        }
    }

    private void runConsole() {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": cmdListEmployees(); break;
                case "2": cmdAddEmployee(); break;
                case "3": cmdRecordAttendance(); break;
                case "4": cmdListAttendanceByDate(); break;
                case "5": cmdExportAttendance(); break;
                case "0": running = false; break;
                default: System.out.println("Unknown option"); break;
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMenu() {
        System.out.println("\n=== Employee Management System ===");
        System.out.println("1) List employees");
        System.out.println("2) Add employee");
        System.out.println("3) Record attendance");
        System.out.println("4) List attendance for date");
        System.out.println("5) Export all attendance to CSV (attendance_export.csv)");
        System.out.println("0) Exit");
        System.out.print("Select: ");
    }

    private void cmdListEmployees() {
        List<Employee> list = repo.listEmployees();
        if (list.isEmpty()) { System.out.println("No employees found."); return; }
        System.out.println("ID | Name | Department");
        list.forEach(e -> System.out.println(e));
    }

    private void cmdAddEmployee() {
        System.out.print("Name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) { System.out.println("Name required."); return; }
        System.out.print("Department: ");
        String dept = scanner.nextLine().trim();
        if (dept.isEmpty()) dept = "General";
        Employee e = repo.addEmployee(name, dept);
        System.out.println("Added: " + e);
    }

    private void cmdRecordAttendance() {
        System.out.print("Employee ID: ");
        String sid = scanner.nextLine().trim();
        int id;
        try { id = Integer.parseInt(sid); } catch (NumberFormatException ex) { System.out.println("Invalid ID."); return; }
        Optional<Employee> emp = repo.findEmployeeById(id);
        if (!emp.isPresent()) { System.out.println("Employee not found."); return; }
        System.out.print("Date (YYYY-MM-DD) [default today]: ");
        String d = scanner.nextLine().trim();
        LocalDate date;
        try { date = d.isEmpty() ? LocalDate.now() : LocalDate.parse(d); } catch (DateTimeParseException ex) { System.out.println("Invalid date."); return; }
        System.out.print("Status (Present/Absent) [Present]: ");
        String status = scanner.nextLine().trim();
        if (status.isEmpty()) status = "Present";
        System.out.print("Notes (optional): ");
        String notes = scanner.nextLine().trim();
        repo.recordAttendance(id, date, status, notes);
        System.out.println("Recorded attendance for " + emp.get().name + " on " + date + " — " + status);
    }

    private void cmdListAttendanceByDate() {
        System.out.print("Date (YYYY-MM-DD) [default today]: ");
        String d = scanner.nextLine().trim();
        LocalDate date;
        try { date = d.isEmpty() ? LocalDate.now() : LocalDate.parse(d); } catch (DateTimeParseException ex) { System.out.println("Invalid date."); return; }
        List<AttendanceRecord> recs = repo.listAttendanceForDate(date);
        if (recs.isEmpty()) { System.out.println("No attendance records for " + date); return; }
        System.out.println("EmployeeID | Name | Status | Notes");
        for (AttendanceRecord r : recs) {
            String name = repo.findEmployeeById(r.employeeId).map(e -> e.name).orElse("Unknown");
            System.out.println(r.employeeId + " | " + name + " | " + r.status + " | " + r.notes);
        }
    }

    private void cmdExportAttendance() {
        Path out = Paths.get("attendance_export.csv");
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("EmployeeID,Name,Date,Status,Notes");
            w.newLine();
            for (AttendanceRecord r : repo.allAttendance()) {
                String name = repo.findEmployeeById(r.employeeId).map(e -> e.name).orElse("Unknown");
                w.write(r.employeeId + "," + Employee.csvEscape(name) + "," + r.date + "," + Employee.csvEscape(r.status) + "," + Employee.csvEscape(r.notes));
                w.newLine();
            }
            System.out.println("Exported to " + out.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
        }
    }

    // Simple embedded HTTP server
    static class WebServer {
        static void startServer(int port, EmployeeRepository repo) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exch -> {
                String path = exch.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";
                Path f = Paths.get(".").resolve(path.substring(1)).normalize();
                if (!f.startsWith(Paths.get(".").toAbsolutePath().normalize()) || !Files.exists(f) || Files.isDirectory(f)) {
                    byte[] not = "Not found".getBytes();
                    exch.sendResponseHeaders(404, not.length);
                    try (OutputStream os = exch.getResponseBody()) os.write(not);
                    return;
                }
                String type = guessContentType(f.getFileName().toString());
                byte[] bytes = Files.readAllBytes(f);
                exch.getResponseHeaders().set("Content-Type", type);
                exch.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exch.getResponseBody()) os.write(bytes);
            });

            server.createContext("/api/employees", exch -> {
                try {
                    if ("GET".equalsIgnoreCase(exch.getRequestMethod())) {
                        List<Employee> list = repo.listEmployees();
                        String json = "[" + list.stream()
                            .map(e -> "{\"id\":" + e.id + ",\"name\":\"" + jsonEscape(e.name) + "\",\"department\":\"" + jsonEscape(e.department) + "\"}")
                            .collect(Collectors.joining(",")) + "]";
                        sendJson(exch, 200, json);
                        return;
                    }
                    if ("POST".equalsIgnoreCase(exch.getRequestMethod())) {
                        String body = new BufferedReader(new InputStreamReader(exch.getRequestBody())).lines().collect(Collectors.joining("\n"));
                        Map<String,String> map = parseJsonBody(body);
                        String name = map.getOrDefault("name","").trim();
                        String dept = map.getOrDefault("department","General").trim();
                        if (name.isEmpty()) { sendJson(exch, 400, "{\"error\":\"name required\"}"); return; }
                        Employee e = repo.addEmployee(name, dept);
                        String json = "{\"id\":" + e.id + ",\"name\":\"" + jsonEscape(e.name) + "\",\"department\":\"" + jsonEscape(e.department) + "\"}";
                        sendJson(exch, 201, json);
                        return;
                    }
                    sendMethodNotAllowed(exch);
                } catch (Exception ex) { sendJson(exch,500,"{\"error\":\"server error\"}"); }
            });

            server.createContext("/api/attendance", exch -> {
                try {
                    if ("GET".equalsIgnoreCase(exch.getRequestMethod())) {
                        String q = exch.getRequestURI().getQuery();
                        String dateStr = null;
                        if (q != null) {
                            for (String part : q.split("&")) {
                                if (part.startsWith("date=")) dateStr = URLDecoder.decode(part.substring(5),"UTF-8");
                            }
                        }
                        if (dateStr == null || dateStr.isEmpty()) { sendJson(exch,400,"{\"error\":\"date query required\"}"); return; }
                        LocalDate date;
                        try { date = LocalDate.parse(dateStr); } catch (Exception exx) { sendJson(exch,400,"{\"error\":\"invalid date\"}"); return; }
                        List<AttendanceRecord> recs = repo.listAttendanceForDate(date);
                        String json = "[" + recs.stream()
                            .map(r -> "{\"employeeId\":" + r.employeeId + ",\"date\":\"" + r.date + "\",\"status\":\"" + jsonEscape(r.status) + "\",\"notes\":\"" + jsonEscape(r.notes) + "\"}")
                            .collect(Collectors.joining(",")) + "]";
                        sendJson(exch,200,json);
                        return;
                    }
                    if ("POST".equalsIgnoreCase(exch.getRequestMethod())) {
                        String body = new BufferedReader(new InputStreamReader(exch.getRequestBody())).lines().collect(Collectors.joining("\n"));
                        Map<String,String> map = parseJsonBody(body);
                        try {
                            int empId = Integer.parseInt(map.getOrDefault("employeeId","0"));
                            LocalDate date = LocalDate.parse(map.getOrDefault("date", LocalDate.now().toString()));
                            String status = map.getOrDefault("status","Present");
                            String notes = map.getOrDefault("notes","");
                            repo.recordAttendance(empId,date,status,notes);
                            sendJson(exch,201,"{\"ok\":true}");
                        } catch (Exception e) { sendJson(exch,400,"{\"error\":\"invalid payload\"}"); }
                        return;
                    }
                    sendMethodNotAllowed(exch);
                } catch (Exception ex) { sendJson(exch,500,"{\"error\":\"server error\"}"); }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("Server running at http://localhost:" + port + "/");
        }

        private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
            byte[] b = json.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }

        private static void sendMethodNotAllowed(HttpExchange ex) throws IOException {
            byte[] b = "Method Not Allowed".getBytes();
            ex.sendResponseHeaders(405, b.length);
            try (OutputStream os = ex.getResponseBody()) os.write(b);
        }

        private static String jsonEscape(String s) {
            if (s == null) return "";
            return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
        }

        private static Map<String,String> parseJsonBody(String body) {
            Map<String,String> map = new HashMap<>();
            String t = body.trim();
            if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length()-1);
            String[] parts = t.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            for (String p : parts) {
                int idx = p.indexOf(':');
                if (idx < 0) continue;
                String k = p.substring(0, idx).trim().replaceAll("^\"|\"$", "");
                String v = p.substring(idx+1).trim();
                v = v.replaceAll("^\"|\"$", "");
                v = v.replace("\\\"", "\"");
                map.put(k, v);
            }
            return map;
        }

        private static String guessContentType(String name) {
            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (name.endsWith(".css")) return "text/css; charset=utf-8";
            if (name.endsWith(".json")) return "application/json; charset=utf-8";
            return "application/octet-stream";
        }
    }
}