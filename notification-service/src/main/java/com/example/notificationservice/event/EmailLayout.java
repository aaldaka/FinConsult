package com.example.notificationservice.event;

import static com.example.notificationservice.event.EmailTemplate.escape;

// the html around every email: a white card with an icon, the title, the text and one button. Mail apps ignore most
// css, so the layout is tables with inline styles, the button is a link inside a coloured cell, and there are no
// images (the icon is an emoji), so nothing is blocked or missing. The fonts are the app's (Playfair Display and Plus Jakarta Sans) where the mail app loads them, Georgia and Arial
// elsewhere
final class EmailLayout {

    private static final String ROYAL = "#1e3a8a";
    private static final String INK = "#0f172a";
    private static final String MUTED = "#475569";
    private static final String HAIRLINE = "#e2e8f0";
    private static final String BACKGROUND = "#e8eef8";
    private static final String SERIF = "'Playfair Display', Georgia, 'Times New Roman', serif";
    private static final String SANS = "'Plus Jakarta Sans', Arial, Helvetica, sans-serif";

    private EmailLayout() {
    }

    // everything is plain text except icon, which is an html entity
    record Content(String subject, String icon, String title, String greeting, String message,
                   String buttonText, String link, String note) {}

    static String page(Content c) {
        String link = escape(c.link());
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light">
                <title>%s</title>
                <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&family=Playfair+Display:wght@700&display=swap" rel="stylesheet">
                </head>
                <body style="margin:0;padding:0;background-color:%s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:%s;">
                <tr><td align="center" style="padding:32px 16px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">

                <tr><td style="background-color:#ffffff;border-radius:16px;padding:40px 32px 36px;text-align:center;">

                <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center"><tr>
                <td style="background-color:#eef2ff;border:1px solid %s;border-radius:50%%;width:96px;height:96px;text-align:center;vertical-align:middle;font-size:44px;line-height:96px;">%s</td>
                </tr></table>

                <h1 style="margin:28px 0 0;font-family:%s;font-size:30px;line-height:1.25;font-weight:700;color:%s;">%s</h1>
                <p style="margin:24px 0 0;font-family:%s;font-size:18px;font-weight:700;color:%s;">%s</p>
                <p style="margin:12px auto 0;max-width:400px;font-family:%s;font-size:15px;line-height:1.6;color:%s;">%s</p>

                <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin-top:28px;"><tr>
                <td style="background-color:%s;border-radius:10px;">
                <a href="%s" target="_blank" style="display:inline-block;padding:14px 32px;font-family:%s;font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;">%s</a>
                </td></tr></table>

                <p style="margin:28px auto 0;max-width:400px;font-family:%s;font-size:13px;line-height:1.6;color:%s;">%s</p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:28px;"><tr>
                <td style="border-top:1px solid %s;padding-top:20px;font-family:%s;font-size:12px;line-height:1.6;color:%s;text-align:center;">
                Button not working? Copy this link into your browser:<br>
                <a href="%s" target="_blank" style="color:%s;word-break:break-all;">%s</a>
                </td></tr></table>

                <p style="margin:28px 0 0;font-family:%s;font-size:13px;line-height:1.6;color:%s;">Thanks,<br>The FinConsult Team</p>
                </td></tr>

                <tr><td style="padding:16px 8px 0;text-align:center;font-family:%s;font-size:11px;line-height:1.6;color:#64748b;">
                You received this email because of an action on your FinConsult account.
                </td></tr>

                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(
                escape(c.subject()), BACKGROUND, escape(c.message()), BACKGROUND,
                HAIRLINE, c.icon(),
                SERIF, INK, escape(c.title()),
                SANS, INK, escape(c.greeting()),
                SANS, MUTED, escape(c.message()),
                ROYAL, link, SANS, escape(c.buttonText()),
                SANS, MUTED, escape(c.note()),
                HAIRLINE, SANS, MUTED, link, ROYAL, link,
                SANS, MUTED,
                SANS);
    }
}
