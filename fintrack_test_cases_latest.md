# FinTrack — Comprehensive Test Cases Document

> **Project**: FinTrack — AI-Powered Personal Finance Tracker  
> **Date**: 14 April 2026  
> **Total Test Cases**: 109  
> **Coverage**: All modules — Onboarding, Auth, SMS, Transactions, Dashboard, Analytics, Budget, Rewards, AI Engine

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Positive Test Case (valid input / expected behavior) |
| ❌ | Negative Test Case (invalid input / error handling) |

---

## Module 1: Splash Screen (`SplashActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 1 | ✅ | App launches and displays splash screen | Open app | Splash layout is visible for ~2 seconds | User taps app icon → sees FinTrack logo |
| 2 | ✅ | First-time user is routed to SetupActivity | `user_verified = false` in SharedPrefs | Navigates to `SetupActivity` after splash | Fresh install → onboarding screen appears |
| 3 | ✅ | Returning verified user is routed to AuthActivity | `user_verified = true` in SharedPrefs | Navigates to `AuthActivity` after splash | User who completed setup → fingerprint screen |
| 4 | ❌ | SharedPreferences key missing entirely | Prefs file deleted or corrupted | Defaults to `false`, routes to `SetupActivity` | Data cleared → treated as new user |
| 5 | ❌ | App killed during splash delay | User force-closes within 2s | No crash; activity simply finishes | Rapid open-close → no ANR |

---

## Module 2: Onboarding — Name Step (`SetupActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 6 | ✅ | Valid first and last name accepted | First: `Kaustubh`, Last: `Sharma` | Proceeds to Age step | Name fields filled → Age layout appears |
| 7 | ❌ | Empty first name | First: ` `, Last: `Sharma` | Error text becomes visible | Blank first name → "Please enter valid name" |
| 8 | ❌ | Empty last name | First: `Kaustubh`, Last: ` ` | Error text becomes visible | Blank last name → error shown |
| 9 | ❌ | Numbers in first name | First: `Kaustubh123`, Last: `Sharma` | Error text visible (fails `isLetter()` check) | `Kaustubh123` → rejected |
| 10 | ❌ | Special characters in name | First: `K@ustubh`, Last: `Sh#rma` | Error text visible | `@` and `#` → not alphabetic |
| 11 | ❌ | Both fields empty | First: ` `, Last: ` ` | Error text visible | Both blank → blocked |
| 12 | ✅ | Single character names | First: `K`, Last: `S` | Accepted (passes `isLetter()` and `isNotEmpty()`) | Initials only → proceeds |
| 13 | ❌ | Name with spaces | First: `Kau stubh`, Last: `Sharma` | Error text visible (space is not a letter) | Internal spaces → rejected |

---

## Module 3: Onboarding — Age Step (`SetupActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 14 | ✅ | Age exactly 18 | Age: `18` | Proceeds to Phone step | `18` → accepted, phone layout visible |
| 15 | ✅ | Age above 18 | Age: `25` | Proceeds to Phone step | `25` → accepted |
| 16 | ✅ | Senior age | Age: `70` | Proceeds to Phone step | `70` → valid |
| 17 | ❌ | Age below 18 | Age: `17` | Alert dialog: "must be 18 or older", app closes | `17` → blocked, `finishAffinity()` |
| 18 | ❌ | Age is 0 | Age: `0` | Alert dialog blocks user | `0` → underage rejection |
| 19 | ❌ | Age is negative | Age: `-5` | `toIntOrNull()` = -5 → fails `>= 18` → blocked | `-5` → alert shown |
| 20 | ❌ | Non-numeric age input | Age: `abc` | `toIntOrNull()` returns null → defaults to 0 → blocked | `abc` → treated as 0 → rejected |
| 21 | ❌ | Empty age field | Age: ` ` | `toIntOrNull()` = null → 0 → blocked | Blank field → rejection dialog |
| 22 | ✅ | Extremely large age | Age: `999` | Proceeds (no upper bound validation) | `999` → accepted
| 23 | ❌ | Decimal age | Age: `18.5` | `toIntOrNull()` = null → 0 → blocked | `18.5` → Not allowed for input |

---

## Module 4: Onboarding — Phone & OTP Verification (`SetupActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 24 | ✅ | Valid 10-digit phone number | Phone: `9876543210` | SMS sent, OTP layout appears | `9876543210` → "Code sent to +91 9876543210" |
| 25 | ✅ | Correct OTP entered manually | OTP: `482917` (matches `generatedOtp`) | `completeVerification()` called → navigate to MainActivity | User types correct 6-digit code → verified |
| 26 | ✅ | Auto-verify checkbox enabled and OTP auto-detected | Incoming SMS with matching OTP, checkbox ON | OTP field auto-filled, verification completes | SMS arrives → field fills → auto-proceed |
| 27 | ❌ | Phone number less than 10 digits | Phone: `98765` | Toast: "Enter a valid phone number" | `98765` → too short → rejected |
| 28 | ❌ | Empty phone field | Phone: ` ` | Toast: "Enter a valid phone number" | Blank → rejected |
| 29 | ❌ | Wrong OTP entered | OTP: `000000` (does not match) | Toast: "Incorrect OTP. Please try again." | Wrong code → rejected |
| 30 | ❌ | SMS permission denied | User denies SMS permission | Toast: "SMS Permissions are required..." | Permission popup → Deny → error shown |
| 31 | ❌ | SMS sending fails (no SIM / airplane mode) | No cellular service |  Task: "Won't send sms, keeps waiting" | Airplane mode → toast error, returns to phone step |
| 32 | ✅ | Resend OTP after timer expires | Wait 15 seconds | "Resend OTP" button becomes enabled, sends new OTP | Timer counts down → button re-enabled |
| 33 | ❌ | Resend OTP before timer expires | Tap resend at timer = 8s | Button is disabled (alpha 0.5), tap has no effect | User spam-taps → nothing happens |
| 34 | ✅ | Change number button returns to phone step | Tap "Change Number" | OTP layout hidden, phone layout visible, timer cancelled | User wants different number → goes back |
| 35 | ❌ | Auto-verify timeout (30s, no SMS arrives) | Auto-verify ON, but SMS never received | Status text: "Auto-detection timed out. Please enter manually." | Poor network → graceful timeout message |

---

## Module 5: Biometric Authentication (`AuthActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 36 | ✅ | Biometric authentication succeeds | Valid fingerprint/face | Navigates to `MainActivity`, finishes AuthActivity | User scans finger → home screen |
| 37 | ❌ | Biometric authentication fails | Wrong fingerprint | Toast: "Authentication failed" | Unknown finger → rejected |
| 38 | ❌ | Biometric error (hardware unavailable) | No biometric sensor | Toast: "Biometric features are currently unavailable" | Old device → fallback login UI shown |
| 39 | ✅ | Retry biometric button works | Tap "Retry Biometric" | Biometric prompt shown again | User dismissed prompt → can retry |

---

## Module 6: SMS Receiver — Real-Time Detection (`SmsReceiver`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 40 | ✅ | Bank debit SMS detected and saved | SMS: `Rs.500 debited from A/C XX1234 at Zomato` | Transaction entity created, saved to Room DB | Zomato order → auto-logged as ₹500 Food |
| 41 | ❌ | Promotional SMS (non-transactional) | SMS: `Flat 50% OFF on Myntra! Shop now!` | Fails `isTransactionBody()` → ignored | Marketing spam → not saved |
| 42 | ❌ | OTP from another app | SMS: `Your OTP for Flipkart is 123456` | Does not contain "FinTrack verification code" → skipped for OTP, but checked for transaction keywords | Flipkart OTP → not treated as FinTrack OTP |
| 43 | ✅ | Credit SMS detected | SMS: `Rs.25000 credited to A/C XX5678. Salary` | `isDebit = false` → saved as income transaction | Salary credit → logged |
| 44 | ❌ | Duplicate SMS within 5 minutes | Same amount + transac id +  merchant + timestamp < 5 min apart | `isDuplicate()` returns true → skipped | Two identical ₹500 Zomato SMS → only first saved |
| 45 | ✅ | Notification triggered for new expense | Valid transaction detected | `NotificationHelper.showNotification()` called | ₹500 at Zomato → push notification appears |

---

## Module 7: SMS Reader — Bulk Historical Sync (`SmsReader`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 46 | ✅ | Read all SMS and extract transactions | SMS permission granted, inbox has bank SMS | Returns list of `TransactionEntity` objects | 50 bank SMS → 35 valid transactions extracted |
| 47 | ❌ | SMS permission not granted | Permission denied | Empty list returned or permission request triggered | No access → no transactions |
| 48 | ✅ | Deduplication within SMS batch | 3 identical SMS (same amount, merchant,same transac within 5 min) | Only 1 survives internal dedup | Triple-fired SMS → 1 saved |
| 49 | ❌ | No bank SMS in inbox | Only personal texts | Empty transaction list | WhatsApp-only user → "No new transactions found" |

---

## Module 8: SMS Parser — Regex Engine (`SmsParser`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 50 | ✅ | Standard debit SMS parsed correctly | `Rs.1500.00 debited from A/C XX1234 at Amazon` | Amount: 1500, Merchant: Amazon, isDebit: true | Clean bank format → perfect parse |
| 51 | ✅ | UPI transaction parsed | `Paid Rs.200 to kaustubh@upi via UPI` | Amount: 200, Merchant: KAUSTUBH | UPI VPA → merchant extracted before `@` |
| 52 | ✅ | Amount with commas parsed | `Rs.1,50,000 credited to A/C` | Amount: 150000 | Indian comma format → correctly stripped |
| 53 | ❌ | SMS with no amount | `Your A/C balance is below minimum` | Returns null (no transaction) | Balance alert → ignored |
| 54 | ❌ | SMS body is empty string | `""` | Returns null | Empty body → no crash |
| 55 | ✅ | Multiple amount patterns in SMS | `Rs.500 debited, Avl Bal Rs.12000` | Extracts first amount (500), not balance | Picks transaction amount, not balance |
| 56 | ❌ | Failed transaction SMS | `Transaction of Rs.999 FAILED at Amazon` | Should be filtered as non-transaction | Failed keyword → ideally not saved |

---

## Module 9: AI Analyzer — Categorization (`AiAnalyzer`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 57 | ✅ | AI model categorizes SMS correctly | 10 bank SMS bodies sent to Gemini | Returns 10 `AiTransaction` objects with merchant + category | Zomato SMS → `Food & Dining`, Amazon → `Shopping` |
| 58 | ✅ | AI returns valid JSON array | Well-formed SMS list | Parsed into structured `AiTransaction` list | `[{"isTransaction":true,"type":"DEBIT","amount":500,"merchant":"Zomato","category":"Food & Dining"}]` |
| 59 | ❌ | AI returns malformed JSON | API glitch or truncated response | Regex fallback parser activates, extracts individual JSON objects | Broken `[{...` → regex finds `{...}` blocks |
| 60 | ❌ | AI model returns "Unknown" as merchant | Gemini can't determine merchant | Local fallback `analyzeSmsLocally()` called for that SMS | `"merchant":"Unknown"` → retried locally |
| 61 | ✅ | Local fallback correctly identifies Zomato | SMS body contains "zomato" | Brand keyword map returns `Zomato` as merchant, `Food & Dining` as category | `brands["Zomato"] = listOf("zomato")` → matched |

---

## Module 10: AI Analyzer — Financial Insights

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 62 | ✅ | AI generates spending insights | 20+ transactions, budget ₹20000 | Returns 3-5 `Insight` objects with title, description, impact | "Burn Rate Alert: At this pace, you'll exceed budget by ₹5000" |
| 63 | ✅ | Local fallback generates insights | AI API fails | `analyzeLocally()` provides rule-based insights | Projected spend > budget → "Burn Rate Alert" |
| 64 | ✅ | Budget on track insight | Total spent < projected budget | Insight: "Budget On Track" with impact "Low" | ₹8000 spent by day 15 of ₹20000 budget → positive |
| 65 | ✅ | Category-heavy insight triggered | Food & Dining > 40% of total spend | Insight: "Food & Dining Heavy" with impact "Medium" | ₹6000 food out of ₹12000 total → flagged |
| 66 | ✅ | Weekend spender insight | Weekend spend > 40% of total | Insight: "Weekend Spender" | ₹5000 on Sat/Sun out of ₹10000 total → flagged |
| 67 | ✅ | Recurring expense detected | Same merchant appears 2+ times | Insight: "Recurring Expense" | Zomato 5 times → "Consider a subscription" |
| 68 | ✅ | Large purchase flagged | Single transaction > ₹2000 | Insight: "Big Purchase" | ₹8000 at Flipkart → flagged |
| 69 | ✅ | Micro-spending detected | 8+ transactions between ₹10-₹200 | Insight: "Micro-Spending" | 12 small chai/snack buys → flagged |
| 70 | ❌ | No transactions at all | Empty transaction list | AI status: "Sync transactions to get AI insights!" | Fresh user → no insights possible |
| 71 | ❌ | AI response doesn't follow TITLE/MESSAGE/IMPACT format | Free-form paragraph returned | `parseAiResponse()` returns empty list → fallback | Bad format → gracefully handled |

---

## Module 11: AI Analyzer — Semantic Search

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 72 | ✅ | Simple keyword search | Query: `Zomato` | All transactions with merchant/category/body containing "Zomato" | `Zomato` → 5 matching transactions |
| 73 | ❌ | Search with no results | Query: `Tesla` | Returns empty list | No Tesla transactions → empty |
| 74 | ✅ | Search cleared (empty query) | Query: ` ` | Resets to current month's filtered transactions | Clear search → default view |
| 75 | ❌ | AI search fails | Gemini API error during search | Falls back to keyword matches | API down → basic string matching used |

---

## Module 12: Transaction Management (`MainActivity`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 76 | ✅ | Add manual transaction | Merchant: `Coffee Shop`, Amount: `150`, Category: `Food & Dining` | Transaction saved to Room DB, appears in list | Manual entry → visible in transactions tab |
| 77 | ❌ | Add transaction with empty merchant | Merchant: ` `, Amount: `150` | Fails `isNotEmpty()` → not saved | Blank merchant → silently rejected |
| 78 | ❌ | Add transaction with zero amount | Merchant: `Shop`, Amount: `0` | Fails `amount > 0` → not saved | ₹0 → rejected |
| 79 | ❌ | Add transaction with negative amount | Merchant: `Shop`, Amount: `-100` | Fails `amount > 0` → not saved | -₹100 → rejected |
| 80 | ❌ | Add transaction with non-numeric amount | Merchant: `Shop`, Amount: `abc` | `toDoubleOrNull()` = null → defaults to 0.0 → rejected | `abc` → treated as 0 → not saved |
| 81 | ✅ | Delete transaction via long-press | Long-press on transaction → "Delete Transaction" | Confirmation dialog → transaction removed from DB | Long press → Delete → gone from list |
| 82 | ✅ | Change transaction category | Long-press → "Change Category" → select "Shopping" | Transaction updated with new category | Food → Shopping reassignment |
| 83 | ✅ | View transaction details on tap | Tap any transaction row | Dialog shows merchant, amount, category, date, body | Tap Zomato ₹500 → full detail popup |
| 84 | ✅ | Clear all data | Tap "Clear Data" → confirm | `repository.deleteAll()` called, all transactions wiped | Confirm delete → empty state |
| 85 | ❌ | Clear data cancelled | Tap "Clear Data" → cancel | No data deleted, dialog dismissed | Cancel → nothing changes |

---

## Module 13: Dashboard & Charts

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 86 | ✅ | Pie chart renders with categories | 5 transactions across 3 categories | PieChart shows 3 colored segments | Food 40%, Shopping 35%, Travel 25% |
| 87 | ❌ | Pie chart with no data | No transactions for selected month | `pieChart.clear()` called, empty chart | New month → blank pie |
| 88 | ✅ | Month spinner changes data | Select "Mar 2026" from spinner | Dashboard filters to March transactions only | Switch to March → different totals |
| 89 | ✅ | Bar chart shows daily spending | Transactions spread across days | BarChart with bars per day of month | Day 1: ₹200, Day 5: ₹800, etc. |
| 90 | ✅ | Animated total value counter | Total changes from ₹5000 to ₹8000 | `ValueAnimator` smoothly increments the displayed number | Counter animates upward |

---

## Module 14: Budget Management

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 91 | ✅ | Budget progress shows correct percentage | Budget: ₹20000, Spent: ₹10000 | Progress bar at 50%, label shows "50%" | Half budget used → 50% bar |
| 92 | ✅ | Under-budget status (green) | Spent: ₹15000, Budget: ₹20000 | Text: "₹5000.00 remaining" in green color | Remaining shown in `#34A853` |
| 93 | ❌ | Over-budget status (red) | Spent: ₹25000, Budget: ₹20000 | Text: "₹5000.00 over budget!" in red color | Overspend shown in `#EA4335` |
| 94 | ✅ | Edit budget via long-press | Long-press budget card → enter ₹30000 → Save | `monthlyBudget` updated, SharedPrefs saved, UI refreshed | ₹20000 → ₹30000 → recalculated |
| 95 | ❌ | Edit budget with zero value | Enter `0` → Save | Fails `newValue > 0` → not saved | ₹0 → rejected |
| 96 | ❌ | Edit budget with negative value | Enter `-5000` → Save | Fails `newValue > 0` → not saved | -₹5000 → rejected |
| 97 | ❌ | Edit budget with empty field | Leave blank → Save | `toDoubleOrNull()` = null → not saved | Blank → no change |

---

## Module 15: Rewards & Gamification

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 98 | ✅ | Daily point awarded for frugal spending | Net spend < 75% of daily budget, not yet awarded today | 1 point added to `user_points` | Daily budget ₹667, spent ₹400 → +1 point |
| 99 | ❌ | No point if already awarded today | `last_rewarded_day` = today | No duplicate points given | Already earned today → skipped |
| 100 | ❌ | No point if overspent | Net spend ≥ 75% of daily budget | Day marked as processed, 0 points | Spent ₹600 of ₹667 → no reward |
| 101 | ✅ | Successful reward redemption | User has 2500 pts, redeems Amazon Gift Card (2500 pts) | Points deducted, success dialog shown | 2500 → 0 pts, "Reward Redeemed!" |
| 102 | ❌ | Insufficient points for redemption | User has 100 pts, tries Netflix Card (4500 pts) | Toast: "Not enough points!" | 100 < 4500 → blocked |
| 103 | ✅ | Reward costs scale with budget | Budget: ₹50000 (higher than default) | `getDynamicCost()` adjusts reward prices | Higher budget → proportionally higher costs |

---

## Module 16: Database & Repository (`TransactionRepository`, `TransactionDao`)

| # | Type | Test Case | Input / Precondition | Expected Result | Example |
|---|------|-----------|---------------------|-----------------|---------| 
| 104 | ✅ | Insert single transaction | Valid `TransactionEntity` | Row inserted, observable Flow emits update | Insert → list updates reactively |
| 105 | ✅ | Insert batch transactions | List of 20 `TransactionEntity` objects | All 20 inserted in single operation | Bulk SMS sync → 20 rows added |
| 106 | ✅ | Duplicate detection works | Same amount + merchant + date within 5 min | `isDuplicate()` returns `true` | ₹500 Zomato at 10:00 AM, again at 10:10 AM → duplicate |
| 107 | ✅ | Non-duplicate allowed | Same merchant, different amount | `isDuplicate()` returns `false` | ₹500 Zomato, then ₹300 Zomato → both saved |
| 108 | ✅ | Delete single transaction | Call `delete(entity)` | Row removed from DB | Delete Zomato ₹500 → gone |
| 109 | ✅ | Delete all transactions | Call `clearAll()` | Table emptied | Clear data → 0 rows |

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| **Total Test Cases** | **109** |
| ✅ Positive Test Cases | 62 |
| ❌ Negative Test Cases | 47 |
| Modules Covered | **16** |

---

## Coverage Matrix

| Module | Positive | Negative | Total |
|--------|----------|----------|-------|
| Splash Screen | 3 | 2 | 5 |
| Onboarding — Name | 2 | 6 | 8 |
| Onboarding — Age | 3 | 7 | 10 |
| Onboarding — Phone/OTP | 4 | 8 | 12 |
| Biometric Auth | 2 | 2 | 4 |
| SMS Receiver | 3 | 3 | 6 |
| SMS Reader (Bulk) | 2 | 2 | 4 |
| SMS Parser | 4 | 3 | 7 |
| AI Categorization | 3 | 2 | 5 |
| AI Insights | 8 | 2 | 10 |
| AI Semantic Search | 2 | 2 | 4 |
| Transaction Management | 6 | 4 | 10 |
| Dashboard & Charts | 4 | 1 | 5 |
| Budget Management | 2 | 5 | 7 |
| Rewards & Gamification | 3 | 3 | 6 |
| Database & Repository | 6 | 0 | 6 |
| **TOTAL** | **62** | **47** | **109** |
