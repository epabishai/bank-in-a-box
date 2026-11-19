package com.example.bank.resources;

import com.example.bank.api.AccountResponse;
import com.example.bank.api.CreateAccountRequest;
import com.example.bank.core.Account;
import com.example.bank.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class AccountResource {
    private final AccountService accountService;

    public AccountResource(AccountService accountService) {
        this.accountService = accountService;
    }

    @POST
    public Response create(@Valid @NotNull CreateAccountRequest request) {
        Account account;
        try {
            account = accountService.create(request);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new BadRequestException("Invalid initial_deposit", exception);
        }
        return Response.status(Response.Status.CREATED).entity(AccountResponse.from(account)).build();
    }

    @GET
    @Path("/{id}")
    public AccountResponse get(@PathParam("id") String id) {
        UUID accountId;
        try {
            accountId = UUID.fromString(id);
            if (!accountId.toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("Invalid UUID");
            }
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Account id must be a UUID", exception);
        }
        return AccountResponse.from(accountService.get(accountId));
    }
}
