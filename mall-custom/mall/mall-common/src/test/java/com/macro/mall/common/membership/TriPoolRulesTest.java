package com.macro.mall.common.membership;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.macro.mall.common.membership.TriPoolRules.*;

class TriPoolRulesTest {
    @Test void confirmedCapsAndShares() {
        long[] caps = {1200,2400,3600,8000,9200,10400,16000,17200,18400,24000,
                25200,26400,32000,33200,34400,40000,41200,42400,48000};
        for (int n=0;n<caps.length;n++) {
            assertEquals(new BigDecimal(caps[n]+".00"),partnerCap(n));
            assertEquals(n+1,partnerShares(n));
        }
        assertEquals(Tier.VISITOR,tier(false,9));
        assertEquals(Tier.MEMBER,tier(true,2));
        assertEquals(Tier.PARTNER,tier(true,3));
        assertEquals(Tier.COFOUNDER,tier(true,9));
    }
    @Test void cofounderCapStartsOnlyAfterQualificationAndTracksPartnerCap() {
        for (int refs=0;refs<=120;refs++) {
            for (Tier t:Tier.values()) {
                assertEquals(t==Tier.COFOUNDER && refs>=9 ? partnerCap(refs) : new BigDecimal("0.00"), cofounderCap(t,refs));
            }
        }
        assertThrows(IllegalArgumentException.class,()->cofounderCap(Tier.COFOUNDER,-1));
    }

    @Test void usersExampleUsesTwelveSharesNotTenPeople() {
        List<Position> people=new ArrayList<>();
        people.add(new Position(1,Tier.MEMBER,2,partnerCap(1),BigDecimal.ZERO));
        people.add(new Position(2,Tier.MEMBER,2,partnerCap(1),BigDecimal.ZERO));
        for(int i=3;i<=10;i++)people.add(new Position(i,Tier.MEMBER,1,partnerCap(0),BigDecimal.ZERO));
        List<Award> awards=allocate(new BigDecimal("1200.00"),people);
        assertEquals(10,awards.size());
        assertEquals(new BigDecimal("200.00"),awards.get(0).amount());
        assertEquals(new BigDecimal("200.00"),awards.get(1).amount());
        assertEquals(new BigDecimal("100.00"),awards.get(2).amount());
        assertTrue(awards.stream().allMatch(a->a.totalShares()==12));
    }
    @Test void eachCompletedGroupAddsEightThousandAndInterveningUsersAddTwelveHundred() {
        assertEquals(new BigDecimal("80000.00"),partnerCap(30));
        assertEquals(new BigDecimal("160000.00"),partnerCap(60));
        for(int n=3;n<=120;n+=3) {
            if(n>3)assertEquals(partnerCap(n-3).add(new BigDecimal("8000.00")),partnerCap(n));
            assertEquals(partnerCap(n).add(new BigDecimal("1200.00")),partnerCap(n+1));
            assertEquals(partnerCap(n).add(new BigDecimal("2400.00")),partnerCap(n+2));
        }
    }
    @Test void capDoesNotResetPreviousReceipts() {
        var awards=allocate(new BigDecimal("20000.00"),List.of(
                new Position(1,Tier.COFOUNDER,10,partnerCap(9),new BigDecimal("23999.00"))));
        assertEquals(new BigDecimal("1.00"),awards.get(0).amount());
        assertEquals(new BigDecimal("24000.00"),awards.get(0).cap());
    }
    @Test void upgradesNeverResetCumulativeReceipts() {
        var awards=allocate(new BigDecimal("9000.00"),List.of(
                new Position(1,Tier.PARTNER,4,partnerCap(3),new BigDecimal("1199.00"))));
        assertEquals(new BigDecimal("6801.00"),awards.get(0).amount());
    }
    @Test void cappedPositionsAreExcludedAndRemaindersAreNeverOverpaid() {
        List<Position> ps=List.of(new Position(1,Tier.MEMBER,100,new BigDecimal("1.00"),new BigDecimal("1.00")),
                new Position(2,Tier.MEMBER,1,null,BigDecimal.ZERO),new Position(3,Tier.MEMBER,2,null,BigDecimal.ZERO));
        var awards=allocate(new BigDecimal("0.05"),ps);
        assertEquals(3,awards.get(0).totalShares());
        assertEquals(new BigDecimal("0.04"),awards.stream().map(Award::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }
    @Test void poolFundingSumsToNineHundredAndReferralRewardIsExact() {
        assertEquals(new BigDecimal("900.00"),Arrays.stream(Pool.values()).map(TriPoolRules::contribution).reduce(BigDecimal.ZERO,BigDecimal::add));
        assertEquals(0,PRICE.multiply(new BigDecimal("0.10")).compareTo(REFERRAL_CASH));
    }
    @Test void rejectsDuplicatePositionsAndInvalidCounts() {
        var p=new Position(1,Tier.MEMBER,1,null,BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,()->allocate(BigDecimal.TEN,List.of(p,p)));
        assertThrows(IllegalArgumentException.class,()->partnerCap(-1));
    }
}
