# Google Sheets Row Mapping Skill
# This file tells the AI exactly which row each transaction category belongs to in the monthly sheet tabs.
# Edit the row numbers to match your actual sheet layout. Leave row as ? if unknown.
# Format:  Category | Row | Notes / AI Instructions

## INCOME
# Income data comes from payslips. Map each payslip line to the correct row.
Gross Pay (Before Tax)        | 6  |
Federal Income Tax            | 7  | Deduction — subtract from gross
NJ State Income Tax           | 8  | Deduction
NJ SDI (Disability Ins.)      | 9  | Deduction
NJ FLI (Family Leave Ins.)    | 10 | Deduction
NJ SUI (Unemployment Ins.)    | 11 | Deduction
Social Security (OASDI)       | 12 | Deduction
Medicare                      | 13 | Deduction
Other Pre-Tax Deductions      | 14 | Any other pre-tax items from payslip
Other Incomes                 | 15 | Any income coming from bank transactions (INCOME / CASH_IN classification) goes here — amount in B15

## HOUSING & RENT
Rent / Mortgage               | 20 |
HOA Fees                      | 21 |
Renter's Insurance            | 22 |

## UTILITIES
# Research the merchant if unsure which utility type it is before placing it.
Electric                      | 24 | Electric bill (PSE&G, Eversource, etc.)
Gas / Heat                    | 25 | Gas heating bill (National Grid, Eversource, etc.)
Water                         | 26 |
Internet                      | 27 | Comcast, Verizon Fios, Spectrum, etc.
Phone / Mobile                | 28 | Verizon, AT&T, T-Mobile, Metro, etc.

## INSURANCE
Health Insurance (Premium)    | 31 | Monthly health insurance premium from payslip or bank
Dental Insurance              | 32 |
Vision Insurance              | 33 |
Auto Insurance                | 34 |
Life Insurance                | 45 |

## TRANSPORTATION
# Gas stations and rideshare (Uber rides, Lyft) both go in Gas / Fuel / Rides.
# Uber Eats / DoorDash / Grubhub goes under Dining, NOT here.
Car Payment                   | 37 | Auto loan installment
Gas / Fuel / Rides            | 38 | Gas stations, Uber rides, Lyft
Parking & Tolls               | 39 | Parking meters, garages, EZPass, toll roads
Car Maintenance               | 40 | Auto parts stores (AutoZone, O'Reilly), mechanics, car wash

## FOOD & DINING
# IMPORTANT: Distinguish carefully between Groceries and Restaurants.
# Groceries: Walmart, Patel Brothers, Bangladeshi Halal Meat Store, Publix, Whole Foods, Trader Joe's, Stop & Shop, ALDI, Costco, BJ's, etc.
# If a merchant is unfamiliar, look it up before assigning — do not guess. Bullhorn and similar non-grocery merchants go under Dining, not Groceries.
# Uber Eats / DoorDash / Grubhub = Dining (food delivery), NOT transport.
Groceries                     | 42 | Supermarkets and grocery chains only
Dining / Restaurants          | 43 | Restaurants, fast food, cafes, food delivery (Uber Eats, DoorDash, Grubhub)
Coffee                        | 44 | Starbucks, Dunkin, local cafes

## HEALTH & WELLNESS
Doctor / Co-pays              | 46 | Doctor visits, urgent care, hospital co-pays
Pharmacy / Prescriptions      | 47 | CVS, Walgreens, Rite Aid, etc.
Gym Membership                | 48 | Planet Fitness, gym, fitness subscriptions

## DEBT PAYMENTS
Student Loans                 | 50 |
Credit Card (Min. Payment)    | 51 | Minimum or full credit card payment
Personal Loan                 | 52 |

## PERSONAL & LIFESTYLE
Clothing                      | 54 | Apparel stores, online clothing purchases
Entertainment                 | 55 | Movies, concerts, events, games
Subscriptions                 | 56 | Netflix, Hulu, Spotify, Disney+, Apple TV+, etc.
Personal Care / Grooming      | 57 | Haircuts, salon, personal care products

## MISCELLANEOUS
Emergency / Unexpected        | 59 | One-off unexpected expenses
Gifts & Donations             | 60 | Gifts, charity, church, donations
Other                         | 61 | Catch-all for anything that does not fit any category above

## INVESTMENTS & SAVINGS
Cryptocurrency                | 66 | Coinbase transactions go here
Stocks                        | 67 | Fidelity transactions go here (including Fid Bkg Svc LLC Moneyline)
Other Investments             | 68 | Any other investment platform — add on top of existing value with description
