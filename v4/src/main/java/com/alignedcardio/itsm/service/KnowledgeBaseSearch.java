package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.kb.KbSearchResult;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseSearch {

    List<KbSearchResult> search(UUID orgId, String q);

    List<KbSearchResult> suggest(UUID orgId, String description);
}
