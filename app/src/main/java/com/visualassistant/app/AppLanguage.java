package com.visualassistant.app;

import java.util.Locale;

public enum AppLanguage {

    ENGLISH(
            "English", "en-IN", "en-IN",
            new Locale("en", "IN"),
            new String[]{
                    "english","capture","photo","scan",
                    "repeat","help","language","again"
            }
    ),
    HINDI(
            "Hindi", "hi-IN", "hi-IN",
            new Locale("hi", "IN"),
            new String[]{
                    "hindi","capture","photo","ले","फोटो",
                    "स्कैन","दोबारा","मदद","भाषा","हिंदी"
            }
    ),
    KANNADA(
            "Kannada", "kn-IN", "kn-IN",
            new Locale("kn", "IN"),
            new String[]{
                    "kannada","capture","photo",
                    "ತೆಗೆ","ಫೋಟೋ","ಸ್ಕ್ಯಾನ್",
                    "ಮತ್ತೆ","ಸಹಾಯ","ಭಾಷೆ","ಕನ್ನಡ"
            }
    ),
    TELUGU(
            "Telugu", "te-IN", "te-IN",
            new Locale("te", "IN"),
            new String[]{
                    "telugu","capture","photo",
                    "తీయి","ఫోటో","స్కాన్",
                    "మళ్ళీ","సహాయం","భాష","తెలుగు"
            }
    ),
    TAMIL(
            "Tamil", "ta-IN", "ta-IN",
            new Locale("ta", "IN"),
            new String[]{
                    "tamil","capture","photo",
                    "எடு","போட்டோ","ஸ்கேன்",
                    "மீண்டும்","உதவி","மொழி","தமிழ்"
            }
    ),
    MARATHI(
            "Marathi", "mr-IN", "mr-IN",
            new Locale("mr", "IN"),
            new String[]{
                    "marathi","capture","photo",
                    "काढ","फोटो","स्कॅन",
                    "पुन्हा","मदत","भाषा","मराठी"
            }
    );

    public final String   displayName;
    public final String   sarvamCode;
    public final String   speechCode;
    public final Locale   ttsLocale;
    public final String[] triggers;

    AppLanguage(String d, String s, String sp,
                Locale l, String[] t) {
        displayName = d;
        sarvamCode  = s;
        speechCode  = sp;
        ttsLocale   = l;
        triggers    = t;
    }

    public boolean isCaptureCommand(String s) {
        s = s.toLowerCase().trim();
        return s.contains("capture") ||
                s.contains("photo")   ||
                s.contains("scan")    ||
                s.contains("ತೆಗೆ")   ||
                s.contains("ಫೋಟೋ")   ||
                s.contains("फोटो")   ||
                s.contains("ले")      ||
                s.contains("తీయి")   ||
                s.contains("ఫోటో")   ||
                s.contains("எடு")    ||
                s.contains("போட்டோ")||
                s.contains("काढ");
    }

    public boolean isRepeatCommand(String s) {
        s = s.toLowerCase().trim();
        return s.contains("repeat") ||
                s.contains("again")  ||
                s.contains("ಮತ್ತೆ") ||
                s.contains("दोबारा")||
                s.contains("మళ్ళీ") ||
                s.contains("மீண்டும்") ||
                s.contains("पुन्हा");
    }

    public boolean isHelpCommand(String s) {
        s = s.toLowerCase().trim();
        return s.contains("help")   ||
                s.contains("ಸಹಾಯ") ||
                s.contains("मदद")   ||
                s.contains("సహాయం")||
                s.contains("உதவி") ||
                s.contains("मदत");
    }

    public boolean isLanguageCommand(String s) {
        s = s.toLowerCase().trim();
        return s.contains("language") ||
                s.contains("change")   ||
                s.contains("ಭಾಷೆ")    ||
                s.contains("भाषा")    ||
                s.contains("భాష")     ||
                s.contains("மொழி")    ||
                s.contains("बदलो");
    }

    public static AppLanguage fromSpoken(String s) {
        if (s == null) return null;
        s = s.toLowerCase().trim();
        if (s.contains("hindi")   ||
                s.contains("हिंदी"))
            return HINDI;
        if (s.contains("kannada") ||
                s.contains("ಕನ್ನಡ"))
            return KANNADA;
        if (s.contains("telugu")  ||
                s.contains("తెలుగు"))
            return TELUGU;
        if (s.contains("tamil")   ||
                s.contains("தமிழ்"))
            return TAMIL;
        if (s.contains("marathi") ||
                s.contains("मराठी"))
            return MARATHI;
        if (s.contains("english"))
            return ENGLISH;
        return null;
    }
}