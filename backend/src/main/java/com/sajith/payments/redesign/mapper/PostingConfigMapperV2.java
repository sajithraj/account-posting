package com.sajith.payments.redesign.mapper;

import com.sajith.payments.redesign.dto.config.PostingConfigRequestV2;
import com.sajith.payments.redesign.dto.config.PostingConfigResponseV2;
import com.sajith.payments.redesign.entity.PostingConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostingConfigMapperV2 {

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "configId", ignore = true)
    @Mapping(target = "createdBy", constant = "SYSTEM")
    @Mapping(target = "updatedBy", constant = "SYSTEM")
    PostingConfig toEntity(PostingConfigRequestV2 request);

    PostingConfigResponseV2 toResponse(PostingConfig config);
}
