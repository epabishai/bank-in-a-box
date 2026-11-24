package com.example.bank.web;

import io.dropwizard.core.setup.Environment;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Application-context only: Jersey's /api/* mapping takes precedence over /. */
public final class SpaAssets extends HttpServlet {
    private static final Pattern HASHED_ASSET =
            Pattern.compile(".*/[^/]+-[A-Za-z0-9_-]{8,}\\.[^/]+");

    public static void register(Environment environment) {
        environment.servlets().addServlet("spa-assets", new SpaAssets()).addMapping("/");
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                || !safePath(path) || path.equals("/api") || path.startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String asset = path.equals("/") ? "/index.html" : path;
        URL resource = findAsset(asset);
        if (resource == null) {
            asset = "/index.html";
            resource = findAsset(asset);
        }
        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (var input = resource.openStream()) {
            String mime = getServletContext().getMimeType(asset);
            response.setContentType(mime == null ? "application/octet-stream" : mime);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", !asset.equals("/index.html")
                    && HASHED_ASSET.matcher(asset).matches()
                    ? "public, max-age=31536000, immutable" : "no-cache");
            if (!"HEAD".equals(request.getMethod())) {
                input.transferTo(response.getOutputStream());
            }
        }
    }

    private static URL findAsset(String path) throws IOException {
        if (path.endsWith("/")) {
            return null;
        }
        URL resource = SpaAssets.class.getResource("/assets" + path);
        if (resource == null) {
            return null;
        }
        // Never stream classloader directory listings, in exploded classes or a JAR.
        if (resource.getProtocol().equals("file")) {
            try {
                return Files.isRegularFile(Path.of(resource.toURI())) ? resource : null;
            } catch (URISyntaxException exception) {
                throw new IOException("Invalid classpath asset URI", exception);
            }
        }
        if (resource.openConnection() instanceof JarURLConnection connection) {
            var entry = connection.getJarEntry();
            return entry != null && !entry.isDirectory() ? resource : null;
        }
        return null;
    }

    private static boolean safePath(String path) {
        // Do not decode or normalize user input into a different classpath location.
        // Reject encoded paths too, including double encoding and encoded separators.
        if (!path.startsWith("/") || path.contains("//")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char character = path.charAt(i);
            if (character <= 32 || character >= 127 || character == '%'
                    || character == '\\' || character == ';') {
                return false;
            }
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
