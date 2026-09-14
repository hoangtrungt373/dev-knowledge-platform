package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.dto.HashResponse;

class HashGeneratorOperationTest {

    private final HashGeneratorOperation operation = new HashGeneratorOperation();

    // The exact reported example, computed over the UTF-8 bytes of a string containing a
    // multi-byte character (the em dash, U+2014) — verified against a real standalone Java
    // harness first, not just trusted from the report, the same discipline
    // Base64EncodeOperationTest's own multi-byte case already establishes.
    @Test
    void computesAllFourDigestsForTheExactReportedExample() {
        HashResponse result = operation.execute("DevKnowledge — Build, Ship, Share");

        assertThat(result.sha1()).isEqualTo("27531de151361018a4442f96b8f849e84f2fd923");
        assertThat(result.sha256()).isEqualTo("a856eaceaf5409ad47c4e49cf3cdd0faa99f6fdf4a6d31146d1ab5c9216f67ee");
        assertThat(result.sha384())
                .isEqualTo("901546809b3ae26f5d444136330f7576132deafe070a499887e112409a8ee1271b11492467110dd60f075ec0f045ef25");
        assertThat(result.sha512()).isEqualTo(
                "54d9770b7926242bddff39e86c1ef386c1ed6e8051f6016d0fe800110cdcc4c91d82606fc1b0d79258eb548f04304159311108c9662f8f2f3d7802bbbb802cca");
    }

    // A second, independently-verified example (same real-harness discipline) confirming the hex
    // formatting is exactly right in both length and lowercase-ness, not just for the one reported
    // input above.
    @Test
    void computesAllFourDigestsForAPlainAsciiInput() {
        HashResponse result = operation.execute("DevKnowledge");

        assertThat(result.sha1()).isEqualTo("35abd90ddf25c6b7ee67d7f5dfbbbc48332b5e87");
        assertThat(result.sha256()).isEqualTo("f8bdef26435989071098c46c93337f649309bb63b00bd773a2abcbecfbfbc68a");
        assertThat(result.sha384())
                .isEqualTo("748998218bfdaf4c38c68158a7ebe9dee9b33b7e28f7c682ed7a4c807679a8ae0d2fff661497b01515b6fc21bc038312");
        assertThat(result.sha512()).isEqualTo(
                "5699a38f9abbdee0e1604f63dc0c2feb0d0f000a45c53ef5f00294ba8326d52cfbb341bac07a26b3aeb0e6855225d8e7cac4f6165dd96fbab249acb107285364");
    }

    @Test
    void everyDigestIsLowercaseHexOfTheExpectedFixedLength() {
        HashResponse result = operation.execute("DevKnowledge");

        assertThat(result.sha1()).hasSize(40).matches("[0-9a-f]{40}");
        assertThat(result.sha256()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(result.sha384()).hasSize(96).matches("[0-9a-f]{96}");
        assertThat(result.sha512()).hasSize(128).matches("[0-9a-f]{128}");
    }

    // Well-known digests of the empty string, from the algorithms' own published test vectors —
    // confirms an empty input is handled the same as any other, not a special case.
    @Test
    void emptyStringHashesToTheAlgorithmsOwnKnownEmptyDigest() {
        HashResponse result = operation.execute("");

        assertThat(result.sha1()).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertThat(result.sha256()).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(result.sha384())
                .isEqualTo("38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b");
        assertThat(result.sha512()).isEqualTo(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
    }
}
