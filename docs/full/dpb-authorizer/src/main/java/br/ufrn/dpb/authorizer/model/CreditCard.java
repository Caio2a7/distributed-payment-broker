package br.ufrn.dpb.authorizer.model;

import java.time.YearMonth;
import java.util.Objects;

public class CreditCard {
    private final String cardNumber;
    private final int expMonth;
    private final int expYear;
    private final CreditCardBrand brand;

    public CreditCard(String cardNumber, int expMonth, int expYear) {
        if (expMonth < 1 || expMonth > 12) {
            throw new IllegalArgumentException("Invalid expMonth");
        }
        if (YearMonth.of(expYear, expMonth).isBefore(YearMonth.now())) {
            throw new IllegalArgumentException("Card is expired");
        }
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}") || !isLuhnValid(cardNumber)) {
            throw new IllegalArgumentException("Invalid cardNumber format");
        }

        this.cardNumber = cardNumber;
        this.expMonth = expMonth;
        this.expYear = expYear;
        this.brand = CreditCardBrand.brandFromCardNumber(cardNumber);
    }

    public enum CreditCardBrand {
        ELO("^(4011|438935|4576|504175|5067|5090|627780|636297|636368|6500)[0-9]{10,12}$"),
        VISA("^4[0-9]{15}$"),
        MASTERCARD("^(5[1-5][0-9]{14}|2(22[1-9]|2[3-9][0-9]|[3-6][0-9]{2}|7[01][0-9]|720)[0-9]{12})$"),
        AMEX("^3[47][0-9]{13}$"),
        HIPERCARD("^(606282[0-9]{10}|3841[0-9]{12})$");

        private final String regex;

        CreditCardBrand(String regex) {
            this.regex = regex;
        }

        public static CreditCardBrand brandFromCardNumber(String number) {
            for (CreditCardBrand brand : values()) {
                if (number.matches(brand.regex)) {
                    return brand;
                }
            }
            throw new IllegalArgumentException("Unsupported card brand");
        }
    }

    public static boolean isLuhnValid(String number) {
        if (number == null || number.isBlank() || !number.matches("\\d+")) {
            return false;
        }

        int luhnSum = 0;
        boolean shouldDouble = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            if (shouldDouble) {
                int luhnDouble = 2 * Character.getNumericValue(number.charAt(i));
                if (luhnDouble > 9) {
                    luhnSum = luhnSum + (luhnDouble - 9);
                } else {
                    luhnSum = luhnSum + luhnDouble;
                }
                shouldDouble = false;
            } else {
                luhnSum = luhnSum + Character.getNumericValue(number.charAt(i));
                shouldDouble = true;
            }
        }
        return luhnSum % 10 == 0;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public int getExpMonth() {
        return expMonth;
    }

    public int getExpYear() {
        return expYear;
    }

    public CreditCardBrand getBrand() {
        return brand;
    }
}
