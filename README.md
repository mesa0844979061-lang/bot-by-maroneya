# Bot by maroneya (Paper 1.21.4+)

ปลั๊กอินเสกกองทัพผู้เล่นปลอม (ชื่อ/สกินสุ่ม ไม่ใช่ซอมบี้) ลาดตระเวนเป็นแถว โจมตี และถอยไปกินแอปเปิลทอง

**ต้องมี:** Paper 1.21.4+ , Java 21 , [Citizens](https://ci.citizensnpcs.co/) (ปลั๊กอินนี้ใช้ Citizens เป็นตัวสร้างผู้เล่นปลอม)

## คำสั่ง
| คำสั่ง | ทำอะไร |
|---|---|
| `/bot <easy\|medium\|high\|max> [จำนวน]` | เสกกองทัพ (ไทยก็ได้: ง่าย/กลาง/สูง/สูงสุด) |
| `/bot frozen` | แช่/ปลดแช่บอท ทุกตัวถือโทเทมสแตก 99 |
| `/bot mode <auto\|patrol\|attack>` | บังคับโหมดกองทัพ |
| `/debot` | ล้างบอททุกตัว |

สิทธิ์: `botarmy.admin` (op) / `botarmy.ignore` (บอทไม่โจมตีคนนี้)

## ระดับ
- **easy**: ดาบเหล็ก+โล่ ใช้โล่ฉลาดระดับพื้นฐาน
- **medium**: เกียร์เพชร, เอนเดอร์เพิร์ล, แอปเปิลทอง (ถอยไปกิน), บางตัวถือโทเทม
- **high**: เนเธอไรต์, ค้อน mace + วินชาร์จ, ขวานหักโล่, กระโดดคริ, โล่ฉลากมาก
- **max**: เนเธอไรต์เอนชาร์จเต็ม, mace Density V/Breach IV/Wind Burst III, ปีก elytra ดำดิ่ง, โทเทม (สลับโล่เมื่อโดนค้อนทุบ), เอนฯ แอปเปิลทอง

## Build
- GitHub: push แล้วดูไฟล์ใน Actions → Artifacts → `BotByMaroneya.jar`
- เครื่องตัวเอง: `mvn package` → `target/BotByMaroneya.jar`
- ถ้า build ไม่ผ่านเพราะหา Citizens ไม่เจอ ให้แก้เลขเวอร์ชัน `citizens-main` ใน `pom.xml`

ค่าปรับแต่งทั้งหมดอยู่ใน `config.yml`

## แจ้งเตือนเข้า/ออกเกม
ตอนเสกบอทจะขึ้น `ชื่อบอท joined the game` และตอน `/debot` จะขึ้น `left the game` ทยอยทีละตัว
ปิดได้ที่ `broadcast-join-leave: false` ใน `config.yml`
