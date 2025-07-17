select 
	distinct(minswap_input.tx_in_id) tx_id,
	encode(tx.hash, 'hex')
from (tx_out join tx_in on tx_out_id=tx_id and tx_out_index=index and address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a') minswap_input
join tx  on tx.id = minswap_input.tx_in_id

	
	
select 
	buyer_output.tx_id,
	encode(tx.hash, 'hex'),
	buyer_output.address,
	buyer_output.fingerprint,
	encode(buyer_output."name", 'escape'),
	buyer_output.quantity,
	buyer_output.value,
	buyer_input.value,
	tx.fee,
	(buyer_input.value - buyer_output.value) price
from (
	select 
		distinct(minswap_input.tx_in_id) tx_id
	from (tx_out join tx_in on tx_out_id=tx_id and tx_out_index=index and address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a') minswap_input
	order by minswap_input.tx_in_id desc
	limit 200
) minswap_tx
join tx on tx.id=minswap_tx.tx_id
join (tx_out join (ma_tx_out mto join multi_asset ma on ma.id = mto.ident) on mto.tx_out_id = tx_out.id) buyer_output on buyer_output.tx_id = tx.id and buyer_output.address != 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a'
join (tx_in join tx_out buyer_input on buyer_input.tx_id=tx_in.tx_out_id and buyer_input.index=tx_in.tx_out_index) on tx_in_id=tx.id and buyer_input.stake_address_id=buyer_output.stake_address_id
where buyer_output.fingerprint = 'asset1ck30aj08he90cne5j99l6e22m05gcpayescqtm'
order by tx.id desc


join (tx_in join tx_out buyer_input on buyer_input.tx_id=tx_in.tx_out_id and buyer_input.index=tx_in.tx_out_index) on tx_in_id=tx.id and buyer_input.stake_address_id=tx_out.stake_address_id
join (tx_out join tx_in on tx_out_id=tx_id and tx_out_index=index and address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a') buyer_output

where tx_out.address != 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and encode(ma."name", 'escape') = 'FLOW'
order by tx.id desc

select 
minswap_tx.id,
min(encode(minswap_tx.hash, 'hex')),
min(encode(minswap_input."name", 'escape')) input_name,
min(encode(minswap_output."name", 'escape')) output_name,
min(minswap_input.fingerprint) input_fp,
min(minswap_output.fingerprint) output_fp,
sum(minswap_output.quantity) - sum(minswap_input.quantity) token_diff,
sum(minswap_output.value) - sum(minswap_input.value) value_diff
from (select distinct(tx.*) from tx_out join tx on tx.id = tx_out.tx_id where address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' order by tx.id desc limit 200) minswap_tx
join (tx_in join tx_out on tx_out.tx_id=tx_in.tx_out_id and tx_out.index=tx_in.tx_out_index left join (ma_tx_out mto join multi_asset ma on ma.id = mto.ident and ma.policy != decode('F5808C2C990D86DA54BFC97D89CEE6EFA20CD8461616359478D96B4C', 'hex')) on mto.tx_out_id = tx_out.id) minswap_input on minswap_input.tx_in_id = minswap_tx.id
left join (tx_out left join (ma_tx_out mto join multi_asset ma on ma.id = mto.ident) on mto.tx_out_id = tx_out.id) minswap_output on minswap_output.tx_id = minswap_tx.id and minswap_output.address = minswap_input.address and minswap_output.address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and minswap_output.policy != decode('F5808C2C990D86DA54BFC97D89CEE6EFA20CD8461616359478D96B4C', 'hex')
where 
	true
	and minswap_tx.hash = decode('11109bacbdb5ce28f4d94eeb9e634fb86e36bd835085c8951b14dcf65d90d5b3', 'hex')
	and minswap_input.address in ('addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a', 'addr1z8p79rpkcdz8x9d6tft0x0dx5mwuzac2sa4gm8cvkw5hcn9u8tw90pn3ehyfmf4w6vju33vnjn42vmpkakma8keka3jsknp382', 'addr1z8p79rpkcdz8x9d6tft0x0dx5mwuzac2sa4gm8cvkw5hcnpjr8krqwuln7kd93h5ntg55nk4nq4k2576f6gwxxt2rwyq7jlwdj')
group by minswap_tx.id
order by minswap_tx.id desc







select 
minswap_tx.id,
encode(minswap_tx.hash, 'hex'),
encode(ma_in."name", 'escape') input_name,
encode(ma_out."name", 'escape') output_name,
((minswap_output.value - minswap_input.value)/1000000) ada,
(mto_out.quantity - mto_in.quantity) token,
(abs(minswap_output.value - minswap_input.value) / abs(mto_out.quantity - mto_in.quantity) / 1000000) price
from tx minswap_tx
join tx_in on tx_in.tx_in_id = minswap_tx.id
join tx_out minswap_input on minswap_input.tx_id=tx_in.tx_out_id and minswap_input.index=tx_in.tx_out_index and minswap_input.address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a'
join tx_out minswap_output on minswap_output.tx_id = minswap_tx.id and minswap_output.address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and minswap_output.value != minswap_input.value
left join ma_tx_out mto_in on mto_in.tx_out_id = minswap_input.id 
left join multi_asset ma_in on ma_in.id = mto_in.ident and ma_in.policy != decode('F5808C2C990D86DA54BFC97D89CEE6EFA20CD8461616359478D96B4C', 'hex')
left join ma_tx_out mto_out on mto_out.tx_out_id = minswap_output.id and mto_out.ident = mto_in.ident and mto_out.quantity != mto_in.quantity
join multi_asset ma_out on ma_out.id = mto_out.ident
--where 
--encode(ma_in."name", 'escape') = 'FLOW'
--minswap_tx.hash = decode('40cdc6e1232f8979a711cf889eea733e6c58586731104d56dd18d207f1a5be0d', 'hex')
order by minswap_tx.id desc
limit 10







with
minswap_tx as (select distinct(tx.*) from tx_out join tx on tx.id = tx_out.tx_id where address = 'addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and hash = decode('b57652a7f05489248a10bed123d963a5a034ea59aeedc1019b250accfa3b77fa', 'hex') order by tx.id desc limit 300) 
,minswap_tx_in as (select minswap_tx.id minswap_tx_id, minswap_tx.hash, tx_out.*, mto.* from minswap_tx join tx_in on tx_in.tx_in_id=minswap_tx.id join tx_out on tx_out.tx_id=tx_in.tx_out_id and tx_out.index=tx_in.tx_out_index left join (ma_tx_out left join multi_asset ma on ma.id = ma_tx_out.ident) mto on mto.tx_out_id = tx_out.id where tx_out.address='addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and (policy is null or policy != decode('F5808C2C990D86DA54BFC97D89CEE6EFA20CD8461616359478D96B4C', 'hex')))
,minswap_tx_out as (select minswap_tx.id minswap_tx_id, minswap_tx.hash, tx_out.*, mto.* from minswap_tx join tx_out on tx_out.tx_id=minswap_tx.id left join (ma_tx_out left join multi_asset ma on ma.id = ma_tx_out.ident) mto on mto.tx_out_id = tx_out.id where tx_out.address='addr1z84q0denmyep98ph3tmzwsmw0j7zau9ljmsqx6a4rvaau66j2c79gy9l76sdg0xwhd7r0c0kna0tycz4y5s6mlenh8pq777e2a' and (policy is null or policy != decode('F5808C2C990D86DA54BFC97D89CEE6EFA20CD8461616359478D96B4C', 'hex')))
select
	minswap_tx_in.minswap_tx_id,
	minswap_tx_in.address_has_script,
	minswap_tx_in.address,
	minswap_tx_in.payment_cred,
	minswap_tx_in.value,
	encode(minswap_tx_in."name", 'escape'),
	-minswap_tx_in.quantity
from minswap_tx_in
union all
select
	minswap_tx_out.minswap_tx_id,
	minswap_tx_out.address_has_script,
	minswap_tx_out.address,
	minswap_tx_out.payment_cred,
	-minswap_tx_out.value,
	encode(minswap_tx_out."name", 'escape'),
	-minswap_tx_out.quantity
from minswap_tx_out
order by minswap_tx_id desc



