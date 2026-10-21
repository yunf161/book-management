package com.yunfeil161.ailibrary.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.ai.business.BusinessSkillRegistry;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Skill必须匹配原接口语义，不能把不完整的PUT或模型提供的身份直接转发。 */
class BusinessSkillContractTests {

  final BusinessSkillRegistry registry = new BusinessSkillRegistry();

  @Test
  void memberApplicationDoesNotRequireApplicantToKnowInternalVersions() {
    var skill = registry.require("member_change_apply");
    assertDoesNotThrow(() ->
      registry.validate(
        skill,
        Map.of(
          "applicationType",
          "EMPLOYEE_PROFILE_CHANGE",
          "reason",
          "调整岗位",
          "requestedPositionName",
          "馆员"
        )
      )
    );
  }

  @Test
  void planCoreFieldsMatchExistingCreateAndFullUpdateContract() {
    var create = registry.require("plan_create");
    assertThrows(WrongFormatException.class, () ->
      registry.validate(create, Map.of("name", "计划"))
    );
    var update = registry.require("plan_update");
    assertThrows(WrongFormatException.class, () ->
      registry.validate(update, Map.of("name", "计划", "version", 1))
    );
    assertDoesNotThrow(() ->
      registry.validate(
        update,
        Map.of(
          "name",
          "计划",
          "targetBooks",
          3,
          "status",
          "DRAFT",
          "version",
          1
        )
      )
    );
  }

  @Test
  void copyCannotReceiveModelSuppliedOwnershipOrBorrowedStatus() {
    var skill = registry.require("copy_create");
    for (String key : new String[] {
      "memberId",
      "roleId",
      "status",
      "url",
      "method",
    }) {
      assertThrows(WrongFormatException.class, () ->
        registry.validate(
          skill,
          Map.of("bookId", 4, "barcode", "B", "location", "A", key, "BORROWED")
        )
      );
    }
  }

  @Test
  void pathIdentifiersCannotInjectRoutesAndBodyCannotChangeApplicationType() {
    var skill = registry.require("borrow_approve");
    assertThrows(WrongFormatException.class, () ->
      registry.validate(
        skill,
        Map.of(
          "applicationId",
          "1/../permissions",
          "expectedVersion",
          0,
          "approvedDays",
          7
        )
      )
    );
    var renew = registry.require("renew_apply");
    var args = registry.validate(
      renew,
      Map.of("borrowRecordId", 7, "requestedDays", 3)
    );
    assertEquals("RENEW", registry.body(renew, args).get("applicationType"));
    assertFalse(args.containsKey("applicationType"));
  }

  @Test
  void booleansNegativePricesAndInvalidDatesAreRejectedBeforePreview() {
    var skill = registry.require("copy_create");
    assertThrows(WrongFormatException.class, () ->
      registry.validate(
        skill,
        Map.of("bookId", true, "barcode", "B", "location", "A")
      )
    );
    assertThrows(WrongFormatException.class, () ->
      registry.validate(
        skill,
        Map.of("bookId", 4, "barcode", "B", "location", "A", "price", -1)
      )
    );
    assertThrows(WrongFormatException.class, () ->
      registry.validate(
        skill,
        Map.of(
          "bookId",
          4,
          "barcode",
          "B",
          "location",
          "A",
          "acquiredAt",
          "2026-02-30"
        )
      )
    );
  }
}
