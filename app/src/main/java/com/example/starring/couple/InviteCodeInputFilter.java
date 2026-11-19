package com.example.starring.couple;

import android.text.InputFilter;
import android.text.Spanned;

import java.util.Locale;

/**
 * 초대코드 입력칸용 필터: 영문/숫자만 남기고 대문자로 바꾼다.
 * LengthFilter보다 먼저 적용해야 "ab c-23k" 같은 입력(공백/하이픈 포함, 붙여넣기)이
 * 길이 제한에 잘리지 않고 "ABC23K"로 들어온다.
 */
public class InviteCodeInputFilter implements InputFilter {

    /** 입력칸에 걸 필터 묶음 (순서 중요: 정리 → 길이 제한) */
    public static InputFilter[] filters() {
        return new InputFilter[]{
                new InviteCodeInputFilter(),
                new InputFilter.LengthFilter(InviteCode.LENGTH)
        };
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        StringBuilder cleaned = new StringBuilder(end - start);
        boolean changed = false;
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            boolean alnum = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!alnum) {
                changed = true;
                continue;
            }
            char upper = String.valueOf(c).toUpperCase(Locale.ROOT).charAt(0);
            if (upper != c) {
                changed = true;
            }
            cleaned.append(upper);
        }
        return changed ? cleaned.toString() : null; // null = 원본 그대로 허용
    }
}
