# Privacy Policy

**App Name:** Cozy Doubling  
**Developer:** Grepho  
**Contact:** cozydoublingdev@gmail.com  
**Effective Date:** July 21, 2026  
**Last Updated:** July 23, 2026

---

## Introduction

Welcome to **Cozy Doubling**. This Privacy Policy explains how Grepho ("we", "us", or "our") collects, uses, stores, and protects your information when you use the Cozy Doubling mobile application (the "App").

By using the App, you agree to the collection and use of information as described in this policy. If you do not agree, please do not use the App.

---

## 1. Information We Collect

### 1.1 Information You Provide

- **Google Account Information:** When you sign in with Google, we receive your Google account ID, display name, and email address from Google. We do not store your email address directly in our database.
- **Display Name:** You may set or update a custom display name within the App.

### 1.2 Information We Generate

- **Player Tag:** A unique, randomly generated tag (e.g., `#XA4K6E9F`) assigned to your account to allow friends to find you without sharing personal details.
- **User ID:** An internal unique identifier linked to your Google account, used to identify your account securely across our services.

### 1.3 Usage and Activity Data

- **Focus Session Data:** We record the start and end time of each focus session, the number of tasks completed, and any task text you enter during a session.
- **Statistics:** Aggregate data derived from your sessions, including total minutes focused, stored as part of your profile.
- **In-App Currency (Leaves):** The number of "leaves" (in-app currency) earned through focus sessions.
- **Equipped Theme:** Which cosmetic theme you have selected.

### 1.4 Social Data

- **Friends List:** The list of user IDs of users you have added as friends, along with the friendship status (pending or accepted).
- **Block List:** The list of user IDs of users you have blocked, preventing them from interacting with you.
- **Presence Data:** When you are in a Focus Room, your display name, active task text, and task progress are broadcast live to other participants in the same room via Supabase Realtime. Live broadcast data is not permanently stored. However, the **last task text and progress from each session** is saved to your focus session record so your friends can see your recent activity.

### 1.5 Purchase and Subscription Data

- **Purchase Status:** Whether you are an active subscriber ("Supporter"). We use **RevenueCat** to manage in-app subscriptions purchased through Google Play. We do not directly process or store your payment card information. Please refer to [RevenueCat's Privacy Policy](https://www.revenuecat.com/privacy/) and [Google Play's Privacy Policy](https://policies.google.com/privacy) for information on how they handle payment data.

### 1.6 Technical Data

- **Internet Connection:** The App requires an internet connection to function. Standard network activity data (such as IP addresses) may be processed by our backend provider (Supabase) in the course of operating the service.

---

## 2. How We Use Your Information

We use the information we collect for the following purposes:

| Purpose | Data Used |
|---|---|
| Authenticate your account | Google account ID, User ID |
| Display your profile to you and your friends | Display Name, Player Tag, Statistics |
| Enable Focus Rooms | Display Name, Task text, Task progress (real-time only) |
| Manage your friends list | User IDs, Player Tags |
| Power the in-app economy (Leaves, Themes) | Leaves balance, Equipped Theme ID |
| Process and verify in-app purchases | RevenueCat User ID (linked to your Supabase User ID), Supporter status |
| Operate and improve the App | Aggregated, anonymized usage statistics |
| Respond to support requests | Email address (if you contact us) |

We do **not** use your data for advertising, sell it to third parties, or use it for any purpose other than operating the App.

---

## 3. How We Share Your Information

We do not sell, trade, or rent your personal information. We share data only as follows:

### 3.1 With Other Users (Limited)

- Your **Display Name** and **Player Tag** are visible to other users so they can find and add you as a friend.
- When you are in a Focus Room, your **Display Name**, **task text**, and **task completion progress** are visible in real-time to other room participants.
- Your publicly visible **statistics** (e.g., total focus minutes) may be visible to your friends.

### 3.2 With Service Providers

We use the following third-party service providers to operate the App. These providers process your data on our behalf according to their own privacy policies:

- **Supabase** (Backend & Authentication): Stores your profile data, focus sessions, and manages authentication. [Supabase Privacy Policy](https://supabase.com/privacy)
- **Google Sign-In / Google Identity Services**: Authenticates you using your Google account. [Google Privacy Policy](https://policies.google.com/privacy)
- **RevenueCat**: Manages in-app subscriptions and purchase validation. [RevenueCat Privacy Policy](https://www.revenuecat.com/privacy/)
- **Google Play**: Processes in-app purchase payments. [Google Play Terms of Service](https://play.google.com/intl/en_us/about/play-terms/)

### 3.3 Legal Requirements

We may disclose your information if required to do so by law, or in response to valid requests by public authorities (e.g., a court or government agency).

---

## 4. Data Retention

- **Active Accounts:** We retain your account data for as long as your account is active.
- **Focus Session Records:** Session records are retained to compute your statistics and are visible in your journey/history.
- **Focus Session Records & Task Text:** Retained to compute your statistics and populate your journey history. The **last task text** from each session is also stored so your friends can see your recent activity. Live broadcast data streamed during a session is not separately persisted.
- **Deleted Accounts:** When you delete your account (via Settings → Danger Zone → Delete Account), all your data — including your profile, focus sessions, statistics, friends list, leaves, and themes — is **permanently and irreversibly deleted** from our database. This action cannot be undone.

---

## 5. Data Security

We take reasonable technical and organizational measures to protect your information:

- All communication between the App and our servers is encrypted in transit using HTTPS/TLS.
- Authentication is handled by Supabase Auth with industry-standard JWT tokens.
- Access to your data is restricted through Row-Level Security (RLS) policies on our database, ensuring you can only access your own data.
- In-app purchases are validated server-side through RevenueCat.

No method of transmission over the internet or electronic storage is 100% secure, and we cannot guarantee absolute security.

---

## 6. Children's Privacy

Cozy Doubling is not directed at children under the age of **13** (or the applicable minimum age in your jurisdiction). We do not knowingly collect personal information from children under 13. If we learn that we have collected personal information from a child under 13, we will take steps to delete that information promptly.

If you are a parent or guardian and believe your child has provided us with personal information, please contact us at **cozydoublingdev@gmail.com**.

---

## 7. Your Rights and Choices

Depending on your location, you may have the following rights regarding your personal data:

- **Access:** Request a copy of the personal data we hold about you.
- **Correction:** Update or correct your display name at any time via Settings.
- **Deletion:** Delete your entire account and all associated data at any time via Settings → Danger Zone → Delete Account.
- **Data Portability:** Request a copy of your data in a portable format.
- **Objection / Restriction:** Object to or request restriction of certain processing of your data.

To exercise any of these rights (other than correction and deletion which are available in-app), please contact us at **cozydoublingdev@gmail.com**.

---

## 8. International Data Transfers

Our services are hosted on Supabase infrastructure, which may be located outside your country of residence. By using the App, you consent to your information being transferred to and processed in countries outside your own, which may have different data protection laws.

---

## 9. Third-Party Links and Services

The App may contain links to third-party websites or services (such as links to our privacy policy page). We are not responsible for the privacy practices of those third parties. We encourage you to review their privacy policies.

---

## 10. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. We will notify you of any material changes by updating the "Last Updated" date at the top of this page. We encourage you to review this Privacy Policy periodically. Your continued use of the App after changes are posted constitutes your acceptance of the updated policy.

---

## 11. Contact Us

If you have any questions, concerns, or requests regarding this Privacy Policy or our data practices, please contact us:

**Email:** cozydoublingdev@gmail.com  
**Developer:** Grepho

---

*This Privacy Policy is effective as of July 21, 2026.*
