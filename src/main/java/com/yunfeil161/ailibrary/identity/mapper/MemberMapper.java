package com.yunfeil161.ailibrary.identity.mapper;

import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberMapper {
  /** 仅查询可联系成员的摘要，SQL固定排除本人、停用成员与AI。 */
  List<com.yunfeil161.ailibrary.communication.contact.dto.ContactBrief> findContacts(
    @Param("q") com.yunfeil161.ailibrary.identity.dto.MemberQueryRequest q,
    @Param("memberId") Long memberId,
    @Param("scopeDepartmentId") Long departmentId
  );

  Long findDepartmentByMemberId(@Param("id") Long id);
  com.yunfeil161.ailibrary.identity.entity.Member findMemberById(
    @Param("id") Long id
  );

  List<Reader> getReadersByPage(@Param("member") Reader reader);
  List<Employee> getEmployeesByPage(@Param("member") Employee employee);

  int countByIdentity(
    @Param("name") String name,
    @Param("email") String email,
    @Param("phone") String phone,
    @Param("excludeId") Long excludeId
  );
  int insertMember(
    @Param("member") com.yunfeil161.ailibrary.identity.entity.Member member
  );
  int updateMember(
    @Param("id") Long id,
    @Param("member") com.yunfeil161.ailibrary.identity.entity.Member member,
    @Param("expectedVersion") Long expectedVersion
  );
  int updatePassword(
    @Param("id") Long id,
    @Param("passwordHash") String passwordHash
  );
  int disableMember(
    @Param("id") Long id,
    @Param("expectedVersion") Long expectedVersion
  );

  int insertReaderSettings(
    @Param("memberId") Long memberId,
    @Param("reader") Reader reader
  );
  int insertReaderInterest(
    @Param("memberId") Long memberId,
    @Param("interest") String interest
  );
  int deleteReaderInterests(@Param("memberId") Long memberId);
  int updateReaderSettings(
    @Param("memberId") Long memberId,
    @Param("reader") Reader reader
  );
  int deleteReaderSettings(@Param("memberId") Long memberId);

  int insertEmployeeProfile(
    @Param("memberId") Long memberId,
    @Param("employee") Employee employee
  );
  int updateEmployeeProfile(
    @Param("memberId") Long memberId,
    @Param("employee") Employee employee
  );
  int deleteEmployeeProfile(@Param("memberId") Long memberId);
}
