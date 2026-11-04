-- 배송지 스냅샷 (10/28) — C9 가 받은 받는 분 · 주소를 주문에 복사해 둡니다 (Member 배송지를 고치거나 지워도 지난 주문은 그대로).
-- 이전 주문은 NULL
ALTER TABLE purchase_order
    ADD COLUMN recipient_name  VARCHAR(20),
    ADD COLUMN recipient_phone VARCHAR(20),
    ADD COLUMN zonecode        VARCHAR(5),
    ADD COLUMN address         VARCHAR(200),
    ADD COLUMN address_detail  VARCHAR(100);
