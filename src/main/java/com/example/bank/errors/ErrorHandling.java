package com.example.bank.errors;

import io.dropwizard.core.setup.Environment;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;

import java.io.IOException;

public final class ErrorHandling {
    private ErrorHandling() {
    }

    public static void register(Environment environment) {
        environment.jersey().register(DomainExceptionMapper.class);
        environment.jersey().register(ValidationExceptionMapper.class);
        environment.jersey().register(JsonExceptionMapper.class);
        environment.jersey().register(JsonRequestInterceptor.class);
        environment.jersey().register(WebExceptionMapper.class);
        environment.jersey().register(UnhandledExceptionMapper.class);
        environment.jersey().register(ErrorResponseFilter.class);
        // Servlet filters can call sendError before Jersey gets control.
        var errorPages = new ErrorPageErrorHandler() {
            @Override
            public boolean errorPageForMethod(String method) {
                return true;
            }
        };
        errorPages.addErrorPage(ErrorPageErrorHandler.GLOBAL_ERROR_PAGE, "/_error");
        environment.getApplicationContext().setErrorHandler(errorPages);
        environment.servlets().addServlet("json-servlet-error", new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                if (request.getDispatcherType() != DispatcherType.ERROR) {
                    response.setStatus(404);
                } else {
                    response.setStatus((Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
                }
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                if (!"HEAD".equals(request.getMethod())) {
                    environment.getObjectMapper().writeValue(response.getOutputStream(),
                            ErrorResponses.forStatus(response.getStatus()));
                }
            }
        }).addMapping("/_error");
    }
}
