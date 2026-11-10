# SpendFlow Android APK

The SpendFlow debug APK has been built and placed in the project root directory:

- **Filename**: `SpendFlow-debug.apk`
- **Path**: `/SpendFlow-debug.apk` (located in root directory)
- **Version**: `1.1` (`versionCode = 2`, `versionName = "1.1"`)
- **Size**: ~25 MB
- **Latest Features Included**:
  - **50-30-20 Income-Based Auto-Adjusting Budget**:
    - **Dynamic Live Auto-Adjustment**: Budgets automatically calculate based on actual incoming monthly income. When the 1st of the month salary arrives, the budget automatically divides into 50% Needs, 30% Wants, and 20% Savings/Debt. When the 15th of the month salary arrives (or freelance/business income arrives), the budget limits auto-scale up dynamically while your already-spent amounts stay intact!
    - **Shared Budgets Between Categories**: Budgets are shared by bucket (`Needs`, `Wants`, `Savings & Debt`) rather than rigid per-category limits.
    - **New Category Budget Group Selector**: When adding or editing a category, users choose which 50/30/20 group it belongs to (`Needs (50%)`, `Wants (30%)`, or `Savings & Debt (20%)`).
    - **Smart Transfer & Loan Accounting**:
      - Transfers into `SAVINGS` accounts automatically count towards the **Savings & Debt (20%)** bucket.
      - Transfers into `CREDIT_CARD` accounts are excluded from Savings & Debt (credit card purchases are already categorized under Needs/Wants).
      - Loan repayments and debt payments count towards **Savings & Debt (20%)**.
    - **Interactive Visual Distribution Graphs & Progress Bars**:
      - Multi-segment 50/30/20 distribution ratio bar (Needs 50% | Wants 30% | Savings & Debt 20%).
      - Dedicated progress cards for Needs, Wants, and Savings & Debt on both the Categories & Budgets screen and the Dashboard.
      - Bucket filter chips (`All`, `Needs`, `Wants`, `Savings & Debt`) for quick category management.
  - **Dynamic Keyword Learning**: Every transaction saved expands the category vocabulary. Custom store names and brands automatically become category keywords.
  - **Transaction History Matching**: Automatic historical matching by past titles and notes.
  - **Multi-Category Disambiguation Prompt**: Interactive prompt when a note matches multiple categories.
  - **MRU Account & Category Ordering**: Pickers dynamically place the most recently used accounts and categories at the top.
  - **Multi-Currency Support**: Real-time currency conversions across world currencies with live rate sync.

### Download Options:
1. **From the File Explorer**:
   In the file browser on the left, locate `SpendFlow-debug.apk` in the root folder, right-click (or click the three dots `⋮`), and choose **Download**.
2. **From AI Studio App Settings**:
   Click the three dots (`⋮`) or the **Settings** menu at the top-right of AI Studio, then select **Export** / **Download APK/AAB**.
