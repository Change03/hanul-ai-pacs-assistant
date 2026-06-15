package com.hanul.aipacs.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanul.aipacs.util.UidUtil;
import org.junit.jupiter.api.Test;

class UidUtilTest {
    @Test
    void acceptsNumericDotSeparatedUid() {
        assertThat(UidUtil.isValidDicomUid("1.2.826.0.1.3680043.10.543.1")).isTrue();
    }

    @Test
    void rejectsBadUidSyntax() {
        assertThat(UidUtil.isValidDicomUid("1.2.bad")).isFalse();
        assertThat(UidUtil.isValidDicomUid("1..2")).isFalse();
        assertThat(UidUtil.isValidDicomUid("1.02.3")).isFalse();
    }
}
