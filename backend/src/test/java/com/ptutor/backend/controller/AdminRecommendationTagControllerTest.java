package com.ptutor.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ptutor.backend.entity.enums.RecommendationTagGroup;
import com.ptutor.backend.entity.enums.RecommendationTagStatus;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.RecommendationTagService;

@ExtendWith(MockitoExtension.class)
class AdminRecommendationTagControllerTest {

    private static final String PATH = "/api/v1/admin/recommendation-tags";

    @Mock RecommendationTagService tagService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminRecommendationTagController(
                tagService, new ApiResponseFactory(Clock.systemUTC()))).build();
    }

    @Test
    void listsTagsWithFilters() throws Exception {
        UUID subjectId = UUID.randomUUID();
        when(tagService.findForAdmin(RecommendationTagGroup.TOPIC, subjectId, RecommendationTagStatus.INACTIVE))
                .thenReturn(List.of());

        mockMvc.perform(get(PATH).param("group", "TOPIC").param("subjectId", subjectId.toString())
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk());

        verify(tagService).findForAdmin(RecommendationTagGroup.TOPIC, subjectId, RecommendationTagStatus.INACTIVE);
    }

    @Test
    void createsTags() throws Exception {
        when(tagService.create(any())).thenReturn(List.of());

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"tags": [{"group": "STYLE", "code": "hai_huoc", "name": "Hài hước"}]}
                        """))
                .andExpect(status().isCreated());

        verify(tagService).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"tags\": []}",
        "{\"tags\": [{\"code\": \"hai_huoc\", \"name\": \"Hài hước\"}]}",
        "{\"tags\": [{\"group\": \"STYLE\", \"code\": \"Hài Hước\", \"name\": \"Hài hước\"}]}",
        "{\"tags\": [{\"group\": \"STYLE\", \"code\": \"ab\", \"name\": \"Hài hước\"}]}",
        "{\"tags\": [{\"group\": \"STYLE\", \"code\": \"hai_huoc\", \"name\": \" \"}]}"
    })
    void rejectsMalformedTagsBeforeTheyReachTheService(String body) throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tagService);
    }

    @Test
    void changesStatus() throws Exception {
        UUID tagId = UUID.randomUUID();

        mockMvc.perform(patch(PATH + "/" + tagId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk());

        verify(tagService).changeStatus(tagId, RecommendationTagStatus.INACTIVE);
    }

    @Test
    void asksForSuggestionsWithinTheAllowedSize() throws Exception {
        UUID subjectId = UUID.randomUUID();

        mockMvc.perform(post(PATH + "/suggestions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\": \"" + subjectId + "\", \"maxTags\": 8}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/suggestions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\": \"" + subjectId + "\", \"maxTags\": 50}"))
                .andExpect(status().isBadRequest());

        verify(tagService).suggestTopics(subjectId, 8);
    }
}
