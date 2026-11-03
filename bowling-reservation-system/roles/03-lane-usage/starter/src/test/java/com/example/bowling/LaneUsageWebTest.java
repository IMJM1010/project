package com.example.bowling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class LaneUsageWebTest {
    @Autowired private MockMvc mockMvc;

    @Test void lanesPageLoads() throws Exception {
        mockMvc.perform(get("/lanes"))
            .andExpect(status().isOk())
            .andExpect(view().name("lanes/list"));
    }

    @Test void laneDetailsLoad() throws Exception {
        mockMvc.perform(get("/lanes/1"))
            .andExpect(status().isOk())
            .andExpect(view().name("lanes/detail"));
    }

    @Test void usageHistoryLoads() throws Exception {
        mockMvc.perform(get("/usages"))
            .andExpect(status().isOk())
            .andExpect(view().name("usages/list"));
    }
}
