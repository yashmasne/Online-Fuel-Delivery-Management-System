package util;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;

/**
 * Sends the "someone filled in the Get in Touch form" email to the site owner.
 *
 * This talks raw SMTP over TLS itself instead of pulling in the JavaMail /
 * Jakarta Mail library, purely because this project has no build tool (no
 * Maven/Gradle) to fetch that dependency - so there's nowhere to put the jar
 * without you manually downloading and dropping it into WEB-INF/lib. A plain
 * socket client needs nothing extra to compile or run.
 *
 * Configuration lives in WEB-INF/mail.properties (see that file's comments
 * for how to fill it in) so real credentials never need to sit in source
 * code that might get shared or committed.
 */
public final class SimpleMailSender {

    private static final String CONFIG_PATH = "/WEB-INF/mail.properties";

    private SimpleMailSender() {
    }

    /** Sends the contact-form notification. Returns quietly (after logging) if mail isn't configured
     *  or the send fails - a mail problem must never stop the message from being saved to the DB. */
    public static void sendContactNotification(jakarta.servlet.ServletContext servletContext,
                                                 String customerName, String customerEmail, String customerMessage) {
        try {
            Properties config = loadConfig(servletContext);
            if (config == null) {
                System.err.println("SimpleMailSender: WEB-INF/mail.properties not found - skipping email notification.");
                return;
            }

            String host = require(config, "smtp.host");
            int port = Integer.parseInt(require(config, "smtp.port"));
            String username = require(config, "smtp.username");
            String password = require(config, "smtp.password");
            String from = config.getProperty("mail.from", username);
            String to = require(config, "mail.to");

            String subject = "New Get in Touch message from " + customerName;
            String body = "You have a new message from the FuelExpress contact form.\r\n\r\n"
                    + "Name: " + customerName + "\r\n"
                    + "Email: " + customerEmail + "\r\n"
                    + "Received: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")) + "\r\n\r\n"
                    + "Message:\r\n" + customerMessage + "\r\n";

            send(host, port, username, password, from, to, customerEmail, subject, body);
        } catch (Exception e) {
            // Never let a mail problem surface to the person who just submitted the form.
            System.err.println("SimpleMailSender: could not send contact notification - " + e.getMessage());
        }
    }

    private static Properties loadConfig(jakarta.servlet.ServletContext servletContext) throws IOException {
        Properties props = new Properties();
        try (var in = servletContext.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                return null;
            }
            props.load(in);
        }
        return props;
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("mail.properties is missing a value for '" + key + "'");
        }
        return value.trim();
    }

    /** Minimal SMTP-over-TLS conversation: connect, EHLO, AUTH LOGIN, MAIL/RCPT/DATA, QUIT. */
    private static void send(String host, int port, String username, String password,
                              String from, String to, String replyTo, String subject, String body)
            throws IOException {

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream rawOut = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true)) {

            expect(reader, 220);

            command(writer, reader, "EHLO fuelexpress.local", 250);
            command(writer, reader, "AUTH LOGIN", 334);
            command(writer, reader, encode(username), 334);
            command(writer, reader, encode(password), 235);
            command(writer, reader, "MAIL FROM:<" + from + ">", 250);
            command(writer, reader, "RCPT TO:<" + to + ">", 250);
            command(writer, reader, "DATA", 354);

            String headers = "From: FuelExpress Website <" + from + ">\r\n"
                    + "To: <" + to + ">\r\n"
                    + "Reply-To: <" + replyTo + ">\r\n"
                    + "Subject: " + subject + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n";

            // Per RFC 5321, a lone "." on a line ends the DATA block - escape any such
            // line in the body so a message that happens to contain one isn't truncated.
            String escapedBody = body.replaceAll("(?m)^\\.", "..");

            writer.print(headers);
            writer.print(escapedBody);
            writer.print("\r\n.\r\n");
            writer.flush();
            expect(reader, 250);

            command(writer, reader, "QUIT", 221);
        }
    }

    private static void command(PrintWriter writer, BufferedReader reader, String line, int expectedCode)
            throws IOException {
        writer.print(line + "\r\n");
        writer.flush();
        expect(reader, expectedCode);
    }

    private static void expect(BufferedReader reader, int expectedCode) throws IOException {
        String line;
        String lastLine = null;
        do {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed the connection unexpectedly");
            }
            lastLine = line;
            // Multi-line responses look like "250-text" until the final "250 text" line.
        } while (line.length() >= 4 && line.charAt(3) == '-');

        int code = Integer.parseInt(lastLine.substring(0, 3));
        if (code != expectedCode) {
            throw new IOException("SMTP server rejected the request: " + lastLine);
        }
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
