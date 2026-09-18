DROP TABLE IF EXISTS prescription_item;
DROP TABLE IF EXISTS prescription;
DROP TABLE IF EXISTS medicine;
DROP TABLE IF EXISTS patient;
DROP TABLE IF EXISTS clinic_guideline;
DROP TABLE IF EXISTS sys_ai_config;

CREATE TABLE IF NOT EXISTS patient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    age INT NOT NULL,
    phone VARCHAR(20),
    allergies VARCHAR(255),
    medical_history VARCHAR(500),
    remarks VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS medicine (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    trade_name VARCHAR(100),
    specification VARCHAR(100),
    category VARCHAR(50),
    stock INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    unit VARCHAR(20),
    default_dosage VARCHAR(100),
    contraindications VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS prescription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prescription_no VARCHAR(64) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    patient_name VARCHAR(50) NOT NULL,
    doctor_name VARCHAR(50) NOT NULL,
    diagnosis VARCHAR(255) NOT NULL,
    ai_advice TEXT,
    total_amount DECIMAL(10, 2) NOT NULL,
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prescription_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    medicine_id BIGINT,
    medicine_name VARCHAR(100) NOT NULL,
    specification VARCHAR(100),
    quantity INT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    dosage VARCHAR(100),
    frequency VARCHAR(50),
    route VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS clinic_guideline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    disease_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    first_line_drugs VARCHAR(255),
    contraindications VARCHAR(500),
    clinical_pathway TEXT
);

CREATE TABLE IF NOT EXISTS sys_ai_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(50) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    model_name VARCHAR(100) NOT NULL,
    temperature DECIMAL(3, 2) DEFAULT 0.3,
    max_tokens INT DEFAULT 2048,
    is_active INT DEFAULT 0,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_salary_slip (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doctor_id VARCHAR(50) NOT NULL,
    doctor_name VARCHAR(50) NOT NULL,
    salary_month VARCHAR(20) NOT NULL,
    base_salary DECIMAL(10, 2) NOT NULL,
    clinic_commission DECIMAL(10, 2) NOT NULL,
    plaster_commission DECIMAL(10, 2) NOT NULL,
    deduction_social DECIMAL(10, 2) NOT NULL,
    tax DECIMAL(10, 2) NOT NULL,
    net_salary DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT '已发放',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS oa_plaster_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_name VARCHAR(50) NOT NULL,
    plaster_type VARCHAR(100) NOT NULL,
    paste_count INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    doctor_name VARCHAR(50) NOT NULL,
    clinic_name VARCHAR(100) NOT NULL,
    therapy_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS oa_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_name VARCHAR(50) NOT NULL,
    approval_type VARCHAR(50) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    start_time VARCHAR(50) NOT NULL,
    end_time VARCHAR(50) NOT NULL,
    duration_days DECIMAL(4, 1) NOT NULL,
    status VARCHAR(20) DEFAULT '待审批',
    approver_name VARCHAR(50),
    comment VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_token_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(100),
    model_name VARCHAR(50),
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    latency_ms BIGINT,
    cost_cny DECIMAL(8, 5),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);