## Natural Language Querying of relational data

Idea of this mini project was to have data in a relational database (Postgres here) and to be able to
query this in natural language.

Data here is from [Kaggle](https://www.kaggle.com/datasets/vainero/restaurants-customers-orders-dataset?resource=download&select=cities.csv)
and the domain is restaurant data with members and orders. I have translated this CSV data into SQL
with the following schema/DDL:

```roomsql
"meal_type" (id INTEGER  NOT NULL PRIMARY KEY, meal_type VARCHAR(7) NOT NULL);
"meal" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_id INTEGER  NOT NULL, meal_type_id  INTEGER  NOT NULL REFERENCES meal_type(id), # price NUMERIC NOT NULL);
"city" (id INTEGER  NOT NULL PRIMARY KEY, city_name VARCHAR(28) NOT NULL);
"member" (id INTEGER  NOT NULL PRIMARY KEY, first_name VARCHAR(10) NOT NULL, surname VARCHAR(11) NOT NULL, sex VARCHAR(1) NOT NULL, email VARCHAR(28) NOT NULL, city_id INTEGER NOT NULL references city(id), monthly_budget NUMERIC(6,1) NOT NULL);
"monthly_member_totals" (member_id INTEGER NOT NULL references member(id), year INTEGER  NOT NULL, month INTEGER  NOT NULL, order_count INTEGER  NOT NULL, meals_count INTEGER  NOT NULL, monthly_budget VARCHAR(18) NOT NULL, total_expense NUMERIC(6,1) NOT NULL, balance VARCHAR(19) NOT NULL, commission VARCHAR(18) NOT NULL, PRIMARY KEY (member_id, year, month));
"restaurant_type" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_type VARCHAR(9) NOT NULL);
"restaurant" (id INTEGER  NOT NULL PRIMARY KEY, restaurant_name VARCHAR(13) NOT NULL, restaurant_type_id INTEGER  NOT NULL references restaurant_type(id), income_percentage  NUMERIC(5,3) NOT NULL, city_id INTEGER  NOT NULL references city(id));
"order" (id INTEGER  NOT NULL PRIMARY KEY, order_date DATE  NOT NULL, order_hour VARCHAR(16) NOT NULL, member_id INTEGER  NOT NULL references member(id), restaurant_id INTEGER  NOT NULL references restaurant(id), total_order VARCHAR(18) NOT NULL);
"order_meals" (id INTEGER  NOT NULL PRIMARY KEY, order_id INTEGER  NOT NULL references "order"(id), meal_id  INTEGER  NOT NULL references meal(id));
```
For the natural language queries to SQL, I have used GPT4 from OpenAI and can ask questions such as:
   - Which cities and dates do the members make the most orders. Show top 10
   - What is the ratio of meal types in restaurants in each city?
   - What is the ratio of the orders in cities with the most Italian restaurants?
   - Which cities have the most vegan meals?
   - What is the difference in the range price of the hot or cold meal?
   - What is the correlation between the sex of members and serve_type?

### Example
Q. Which cities and dates do the members make the most orders. Show top 10

A.
```shell
+----------------+------------+-------------+
| city_name      | order_date | order_count |
+----------------+------------+-------------+
| Herzelia       | 2020-03-01 | 158         |
| Ramat Gan      | 2020-03-01 | 106         |
| Ramat Hasharon | 2020-03-01 | 103         |
| Ramat Hasharon | 2020-03-02 | 100         |
| Tel Aviv       | 2020-03-02 | 98          |
| Herzelia       | 2020-02-28 | 89          |
| Herzelia       | 2020-05-01 | 89          |
| Herzelia       | 2020-03-02 | 88          |
| Ramat Hasharon | 2020-05-01 | 87          |
| Tel Aviv       | 2020-03-01 | 86          |
+----------------+------------+-------------+
```

### To run
1. Copy GPT4 API key into the config in `Main`
2. Run docker-compose for Postgres dependency
3. Run `Main` app after modifying question in the `Main` app
