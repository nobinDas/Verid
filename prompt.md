Task: Bank Statement → Monthly Google Sheet (Create or Update)
Using the existing codebase and statement parsing logic already in this project, implement the following feature:
Trigger: A bank statement is uploaded by the user and process button clicked and processed.

Behavior:
After the statement is parsed, extract the month and year from the transaction data (e.g. 2025-06 → sheet name "Jun 2025" or whatever naming convention fits the existing sheets if any already exist).
Check the target Google Sheet workbook for a tab matching that month:

If the tab does not exist: create it, then write the transactions as described at the end of this file.
If the tab already exists: update the new transaction in the necessary cell. if in the same cell new information need to be added then it should add with method like addition for or substruction as needed.


After writing, auto-resize columns for readability if the Sheets API supports it in the current integration.
Return a clear success/error message indicating whether the sheet was created or updated, and how many rows were written.

Constraints:

Reuse the existing Google Sheets connection/auth setup — do not introduce a new auth flow.
Reuse the existing statement parsing output — do not re-parse from scratch.
Handle edge cases: empty statement, unreadable month, API failure.
Keep this as a single self-contained function or module that can be called from the existing upload handler.
the statements will not necessarily include only one months transaction it will from the middle of the one month and will end on the other month so the transaction need to be added in the accurate sheet according to the months and dates. for example the statment can be of date 22nd may but it can start with the transaction from 22nd april. so add the transaction of the april month on the sheet of april and then make a new sheet for may if does not exist and add the may transaction on that sheet.

if you need to connect with google sheet with api or something let me know. 

I want the google sheet format to be like this:
June — Personal Finance Tracker						
Color Legend:  Blue = Input values  |  Black = Calculated formulas  |  Green = Links from other cells						
						
💰  INCOME  (Bi-Weekly Pay Periods)						
Category	Pay Period 1	Pay Period 2	Month Total			
Gross Pay (Before Tax)	-	-	-			
Federal Income Tax	-	-	-			
NJ State Income Tax	-	-	-			
NJ SDI (Disability Ins.)	-	-	-			
NJ FLI (Family Leave Ins.)	-	-	-			
NJ SUI (Unemployment Ins.)	-	-	-			
Social Security (OASDI)	-	-	-			
Medicare	-	-	-			
Other Pre-Tax Deductions	-	-	-			
Net Take-Home Pay	-	-	-			
						
💳  EXPENSES						
Category	Description / Notes	Amount	Due Date	Paid?		
🏠 Housing						
Rent / Mortgage		-		No		
HOA Fees		-		No		
Renter's Insurance		-		No		
⚡ Utilities & Bills						
Electric		-		No		
Gas / Heating		-		No		
Water & Sewer		-		No		
Internet		-		No		
Cell Phone		-		No		
Cable / Streaming		-		No		
🛡️ Insurance						
Health Insurance (Premium)		-		No		
Dental Insurance		-		No		
Vision Insurance		-		No		
Auto Insurance		-		No		
Life Insurance		-		No		
🚗 Transportation						
Car Payment		-		No		
Gas / Fuel		-		No		
Parking & Tolls		-		No		
Car Maintenance		-		No		
🍔 Food & Dining						
Groceries		-		No		
Restaurants / Takeout		-		No		
Coffee Shops		-		No		
🏥 Health & Wellness						
Doctor / Co-pays		-		No		
Pharmacy / Prescriptions		-		No		
Gym Membership		-		No		
🎓 Debt Payments						
Student Loans		-		No		
Credit Card (min. payment)		-		No		
Personal Loan		-		No		
🎉 Personal & Lifestyle						
Clothing		-		No		
Entertainment		-		No		
Subscriptions (Netflix, etc.)		-		No		
Personal Care / Grooming		-		No		
💡 Miscellaneous						
Emergency / Unexpected		-		No		
Gifts & Donations		-		No		
Other		-		No		
TOTAL EXPENSES		-				
						
📈  INVESTMENTS						
Investment Type	Amount Invested ($)	Notes / Ticker				
Cryptocurrency	-					
Stocks	-					
Others	-					
Others	-					
Others	-					
Others	-					
TOTAL INVESTMENTS	-					
						
📊  MONTHLY SUMMARY						
Total Gross Income			-			
Total Tax Paid			-			
Total Take-Home Pay			-			
Total Expenses			-			
Total Investments			-			
Net Remaining			-			