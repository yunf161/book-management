package com.yunfeil161.ailibrary.recommendation.mapper;

import com.yunfeil161.ailibrary.recommendation.entity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 推荐快照、有限历史和反馈的数据访问。所有成员 ID 来自可信会话。 */
@Mapper
public interface RecommendationMapper {
  RecommendationBatch current(Long memberId);
  RecommendationBatch byRequest(
    @Param("memberId") Long memberId,
    @Param("requestId") String requestId
  );
  List<RecommendationBatch> batches(Long memberId);
  List<RecommendationItem> items(Long batchId);
  List<Long> recentBooks(Long memberId);
  List<RecommendationFeedback> feedback(Long memberId);
  List<Long> borrowedBooks(Long memberId);
  Boolean personalizationEnabled(Long memberId);
  List<RecommendationCandidate> candidates(
    @Param("memberId") Long memberId,
    @Param("cutoff") LocalDateTime cutoff,
    @Param("personalized") boolean personalized
  );
  int archive(Long memberId);
  int invalidate(Long memberId);
  int insertBatch(RecommendationBatch batch);
  int insertItem(RecommendationItem item);
  int insertFeedback(RecommendationFeedback feedback);
  int archiveFeedback(Long memberId);
  List<Long> historicalFeedbackIds(Long memberId);
  int deleteFeedback(Long id);
  int deleteItems(Long batchId);
  int deleteBatch(Long batchId);
}
