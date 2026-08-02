package com.example.recipemanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// app.jwt.secret is overridden here because JwtService's constructor now
// refuses to start when it's left at the shipped application.properties
// default (see JwtService.SHIPPED_DEFAULT_SECRET) — this is a full context
// load, so it needs a real (if fake) secret like any other deployment would.
@SpringBootTest
@TestPropertySource(properties = "app.jwt.secret=93nVNqbyLh/RSvsAb1FIlGeVkimlTQ8WxAvLWegsAsQ=")
class RecipeManagerApplicationTests {

    @Test
    void contextLoads() {
    }
}
