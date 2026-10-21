package com.yunfeil161.ailibrary.identity.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 读者领域对象，由 member、reader_interest 和 reader_settings 组装。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Reader extends Member {
    private Long readerInterestId;
    private String interestName;
    private String interests;
    private Long readerSettingsId;
    private Boolean personalizationEnabled;
    private Boolean recommendationNotificationsEnabled;
}
