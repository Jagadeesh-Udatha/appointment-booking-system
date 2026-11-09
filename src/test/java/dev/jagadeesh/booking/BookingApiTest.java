package dev.jagadeesh.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole flow over HTTP with real tokens: a business signs up, sets up a service, a customer books. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class BookingApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    @Test
    void businessSetsUpAndCustomerBooks() throws Exception {
        clock.set(TestClockConfig.START);
        String tenant = read(call(post("/api/tenants"), null, """
                {"name":"QuickFix Service Centre","timezone":"Asia/Kolkata","cancelCutoffMinutes":60,"holdMinutes":10,
                 "adminEmail":"owner@quickfix.test","adminName":"Owner","adminPassword":"password-123"}""", 201)).get("id").asText();
        String admin = login(tenant, "owner@quickfix.test");

        String service = read(call(post("/api/services"), admin,
                "{\"name\":\"AC repair visit\",\"durationMinutes\":60,\"bufferMinutes\":15,\"price\":499}", 201)).get("id").asText();
        String tech = read(call(post("/api/providers"), admin,
                "{\"name\":\"Imran\",\"kind\":\"Technician\",\"serviceIds\":[\"" + service + "\"]}", 201)).get("id").asText();
        call(put("/api/providers/" + tech + "/working-hours"), admin,
                "[{\"day\":\"TUESDAY\",\"start\":\"10:00\",\"end\":\"14:00\"}]", 204);

        call(post("/api/tenants/" + tenant + "/customers"), null,
                "{\"email\":\"meera@mail.test\",\"fullName\":\"Meera\",\"password\":\"password-123\"}", 201);
        String customer = login(tenant, "meera@mail.test");

        JsonNode slots = read(mvc.perform(get("/api/slots").header("Authorization", "Bearer " + customer)
                .param("providerId", tech).param("serviceId", service).param("date", "2030-01-08")).andReturn().getResponse().getContentAsString());
        String first = slots.get(0).asText(); // 10:00 IST

        String booking = read(call(post("/api/bookings"), customer,
                "{\"providerId\":\"" + tech + "\",\"serviceId\":\"" + service + "\",\"startAt\":\"" + first + "\"}", 201)).get("id").asText();
        mvc.perform(post("/api/bookings/" + booking + "/confirm").header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        // the front desk sees it on the day sheet; a second customer can't take the same time
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + admin).param("providerId", tech).param("date", "2030-01-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(booking));
        call(post("/api/tenants/" + tenant + "/customers"), null,
                "{\"email\":\"arun@mail.test\",\"fullName\":\"Arun\",\"password\":\"password-123\"}", 201);
        call(post("/api/bookings"), login(tenant, "arun@mail.test"),
                "{\"providerId\":\"" + tech + "\",\"serviceId\":\"" + service + "\",\"startAt\":\"" + first + "\"}", 409);
    }

    @Test
    void customerCannotCreateServicesAndAnonymousCannotBook() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        String tenant = read(call(post("/api/tenants"), null, """
                {"name":"Glow Salon","timezone":"Asia/Kolkata","cancelCutoffMinutes":60,"holdMinutes":10,
                 "adminEmail":"owner@glow.test","adminName":"Owner","adminPassword":"password-123"}""", 201)).get("id").asText();
        call(post("/api/tenants/" + tenant + "/customers"), null,
                "{\"email\":\"priya@mail.test\",\"fullName\":\"Priya\",\"password\":\"password-123\"}", 201);
        call(post("/api/services"), login(tenant, "priya@mail.test"),
                "{\"name\":\"Haircut\",\"durationMinutes\":30,\"bufferMinutes\":0,\"price\":300}", 403);
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        call(post("/api/auth/login"), null, "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"email\":\"x@y.z\",\"password\":\"nope\"}", 401);
    }

    private String login(String tenant, String email) throws Exception {
        return read(call(post("/api/auth/login"), null,
                "{\"tenantId\":\"" + tenant + "\",\"email\":\"" + email + "\",\"password\":\"password-123\"}", 200)).get("accessToken").asText();
    }

    private String call(MockHttpServletRequestBuilder req, String token, String body, int expected) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private JsonNode read(String s) throws Exception {
        return json.readTree(s.isEmpty() ? "{}" : s);
    }
}
