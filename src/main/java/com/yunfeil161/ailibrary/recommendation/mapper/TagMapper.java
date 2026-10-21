package com.yunfeil161.ailibrary.recommendation.mapper;

import com.yunfeil161.ailibrary.recommendation.entity.InterestTag;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 标签字典和人员、书目关联数据访问；不增加另一张人员兴趣表。 */
@Mapper
public interface TagMapper {
  List<InterestTag> find(
    @Param("id") Long id,
    @Param("code") String code,
    @Param("name") String name
  );
  InterestTag findById(Long id);
  InterestTag findByName(String name);

  /** 当前读可见另一个事务刚提交的标签，用于旧文本兴趣的并发字典初始化。 */
  InterestTag findByNameCurrent(String name);
  int insert(InterestTag tag);
  List<InterestTag> memberTags(Long memberId);
  List<InterestTag> bookTags(Long bookId);
  int insertMemberTag(
    @Param("memberId") Long memberId,
    @Param("tag") InterestTag tag
  );
  int deleteMemberTags(Long memberId);
  int deleteBookTags(Long bookId);
  int insertBookTag(@Param("bookId") Long bookId, @Param("tagId") Long tagId);
}
