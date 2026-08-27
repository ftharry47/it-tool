package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbArticleResponse;
import com.alignedcardio.itsm.api.kb.KbArticleUpdateRequest;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.KbArticle;
import com.alignedcardio.itsm.entity.KbArticleVersion;
import com.alignedcardio.itsm.repository.KbArticleRepository;
import com.alignedcardio.itsm.repository.KbArticleVersionRepository;
import com.alignedcardio.itsm.repository.KbFeedbackRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KbArticleRepository kbArticleRepository;

    @Mock
    private KbArticleVersionRepository kbArticleVersionRepository;

    @Mock
    private KbFeedbackRepository kbFeedbackRepository;

    @Mock
    private KnowledgeBaseSearch knowledgeBaseSearch;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private KnowledgeBaseService knowledgeBaseService;

    private UUID orgId;
    private AppUser author;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        author = new AppUser();
        author.setId(UUID.randomUUID());
        author.setEmail("author@example.com");
        author.setDisplayName("Author");
        author.setObjectId("auth0|123");
    }

    private KbArticle publishedArticle() {
        KbArticle a = new KbArticle();
        a.setId(UUID.randomUUID());
        a.setOrgId(orgId);
        a.setTitle("Original Title");
        a.setCategory("Network");
        a.setBody("Original body.");
        a.setStatus(KbArticle.Status.PUBLISHED);
        a.setVersion(1);
        a.setAuthor(author);
        return a;
    }

    @Test
    void publishedEditCreatesVersionAndIncrementsVersion() {
        KbArticle article = publishedArticle();
        when(kbArticleRepository.findByOrgIdAndId(orgId, article.getId())).thenReturn(Optional.of(article));
        when(kbArticleRepository.save(any(KbArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        KbArticleResponse response = knowledgeBaseService.update(
                author, orgId, article.getId(),
                new KbArticleUpdateRequest("Updated Title", "Network", "Updated body.", null));

        assertEquals(2, response.version());

        ArgumentCaptor<KbArticleVersion> versionCaptor = ArgumentCaptor.forClass(KbArticleVersion.class);
        verify(kbArticleVersionRepository).save(versionCaptor.capture());
        KbArticleVersion savedVersion = versionCaptor.getValue();
        assertEquals(1, savedVersion.getVersion());
        assertEquals("Original Title", savedVersion.getTitle());
        assertEquals("Original body.", savedVersion.getBody());
    }

    @Test
    void draftEditDoesNotCreateVersion() {
        KbArticle article = publishedArticle();
        article.setStatus(KbArticle.Status.DRAFT);
        when(kbArticleRepository.findByOrgIdAndId(orgId, article.getId())).thenReturn(Optional.of(article));
        when(kbArticleRepository.save(any(KbArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        KbArticleResponse response = knowledgeBaseService.update(
                author, orgId, article.getId(),
                new KbArticleUpdateRequest("Updated Draft", "Network", "Updated draft body.", null));

        assertEquals(1, response.version());
        verify(kbArticleVersionRepository, never()).save(any());
    }
}
