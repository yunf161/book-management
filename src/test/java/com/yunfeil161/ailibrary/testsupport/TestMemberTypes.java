package com.yunfeil161.ailibrary.testsupport;

import com.yunfeil161.ailibrary.common.enums.MemberType;

/** 为仍使用数字夹具编号的历史测试生成当前成员领域类型。 */
public final class TestMemberTypes {

  private TestMemberTypes() {}

  public static MemberType fromFixtureCode(int value) {
    return switch (value) {
      case 1 -> MemberType.READER;
      case 2, 3 -> MemberType.EMPLOYEE;
      case 4 -> MemberType.AI_LIBRARIAN;
      default -> throw new IllegalArgumentException("未知的测试成员编号：" + value);
    };
  }
}
