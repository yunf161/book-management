package com.yunfeil161.ailibrary.identity.application.service;

import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeDecisionRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeQueryRequest;
import com.yunfeil161.ailibrary.identity.application.entity.MemberChangeApplication;

public interface MemberChangeApplicationService {
  MemberChangeApplication create(MemberChangeCreateRequest request);

  PageResult<MemberChangeApplication> queryMine(MemberChangeQueryRequest query);

  PageResult<MemberChangeApplication> queryForReview(MemberChangeQueryRequest query);

  MemberChangeApplication cancel(Long id, Long expectedVersion);

  MemberChangeApplication decide(Long id, MemberChangeDecisionRequest request);
}
