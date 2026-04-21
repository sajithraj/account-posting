package com.sr.accountposting.service.processor;

import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;

import java.util.List;

public interface PostingProcessorService {
    ProcessingResult process(PostingJob job, List<PostingConfigEntity> configs);
}
